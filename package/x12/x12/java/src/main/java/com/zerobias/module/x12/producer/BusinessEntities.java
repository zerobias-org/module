package com.zerobias.module.x12.producer;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.materializer.EntityGraph;
import com.zerobias.module.x12.producer.mapping.EntityMapping;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The business layer of the object tree (DESIGN §8.5): collections of named entities projected
 * out of the graph, and the segments that scope them.
 *
 * <pre>
 * /x12-receiver
 * ├─ /claims                       collection — one element per CLP loop, Claim schema
 * │   ├─ /claims/by-file/&lt;fileId&gt;      the same rows, scoped to one interchange
 * │   └─ /claims/by-payerName/&lt;value&gt;  scoped to one payer (and by-payeeNpi, by-check…)
 * ├─ /service-lines  …
 * └─ /remittances    …
 * </pre>
 *
 * <p>Segment children are emergent, exactly like {@code /by-type}: they come from
 * {@code SELECT DISTINCT} over the dimensions actually present, so a payer node appears the
 * first time that payer sends something and never has to be configured.
 *
 * <p>Scoping is pushed into SQL (grain, file, dimension). A user filter is applied to the
 * projected rows, because a business column can sit behind a qualifier predicate or inside a
 * composite, which SQL over the value table cannot express — so filtered pages read the scoped
 * set and page after filtering. Correct, and bounded by the segment rather than the buffer;
 * pushing the compilable subset down is a follow-up, and the value indexes are already there
 * for it (DESIGN §8.5.1).
 */
public final class BusinessEntities {

    /** Segment prefix under a business collection, e.g. {@code /claims/by-file/<id>}. */
    static final String BY = "by-";
    static final String FILE_SEGMENT = "file";

    private final BufferStore buffer;
    private final Map<String, EntityMapping> byCollection = new LinkedHashMap<>();

    public BusinessEntities(BufferStore buffer, List<EntityMapping> mappings) {
        this.buffer = buffer;
        for (EntityMapping m : consolidate(mappings)) {
            byCollection.put(m.collection(), m);
        }
    }

    /**
     * Every bundled guide's mappings, one per collection, in guide order. An anchor-grain
     * collection belongs to the first guide that declares it (its grain is that guide's loop);
     * a dimension-grain collection declared by several guides is merged into one entity
     * spanning them, so {@code /payers} is every payer whichever transaction named it.
     */
    public static List<EntityMapping> mappingsFor(List<String> guides) {
        final List<EntityMapping> all = new ArrayList<>();
        final Set<String> seenGuides = new LinkedHashSet<>();
        for (String gs08 : guides == null ? List.<String>of() : guides) {
            final List<EntityMapping> ms = EntityMapping.forGuide(gs08);
            // aliases (005010X223A1 → 005010X223A2) resolve to the same mapping: read it once
            if (!ms.isEmpty() && seenGuides.add(ms.get(0).gs08())) {
                all.addAll(ms);
            }
        }
        return consolidate(all);
    }

    private static List<EntityMapping> consolidate(List<EntityMapping> mappings) {
        final Map<String, EntityMapping> out = new LinkedHashMap<>();
        for (EntityMapping m : mappings == null ? List.<EntityMapping>of() : mappings) {
            final EntityMapping existing = out.get(m.collection());
            if (existing == null) {
                out.put(m.collection(), m);
                continue;
            }
            final EntityMapping merged = EntityMapping.merge(existing, m);
            if (merged != null) {
                out.put(m.collection(), merged);
            } else if (existing != m) {
                org.slf4j.LoggerFactory.getLogger(BusinessEntities.class).warn(
                    "collection '{}' is declared by {} and {}; the first declaration stands",
                    m.collection(), existing.guides(), m.guides());
            }
        }
        return List.copyOf(out.values());
    }

    public boolean isEmpty() {
        return byCollection.isEmpty();
    }

    /** The collection names, in mapping order: {@code remittances}, {@code claims}, … */
    public List<String> collections() {
        return List.copyOf(byCollection.keySet());
    }

    public EntityMapping mapping(String collection) {
        return byCollection.get(collection);
    }

    /** The dimensions a collection can be segmented by, in mapping order. */
    public List<String> segments(String collection) {
        final EntityMapping m = byCollection.get(collection);
        if (m == null) {
            return List.of();
        }
        if (m.isDimensionGrain()) {
            return List.of();   // a party is already the segment; there is nothing to scope it by
        }
        final List<String> out = new ArrayList<>();
        out.add(FILE_SEGMENT);
        for (EntityMapping.Dimension d : m.dimensions()) {
            out.add(d.name());
        }
        return out;
    }

    /** The values a segment takes right now — emergent children, never configured. */
    public List<String> segmentValues(String collection, String segment) throws SQLException {
        final EntityMapping m = byCollection.get(collection);
        if (m == null) {
            return List.of();
        }
        if (m.isDimensionGrain()) {
            return List.of();
        }
        if (FILE_SEGMENT.equals(segment)) {
            return buffer.distinctAnchorFiles(m.anchorSchemaId());
        }
        // Dimensions are shared by name across guides (an 835 and an 837 both have a
        // payerName), so the distinct values are narrowed to those that actually scope a row
        // of THIS entity — otherwise /claims/by-payerName would list payers that only ever
        // sent an 837 and lead to an empty page.
        final List<String> out = new ArrayList<>();
        for (String value : buffer.distinctDim(segment)) {
            if (buffer.anchorCount(m.anchorSchemaId(), null, segment, value) > 0) {
                out.add(value);
            }
        }
        return out;
    }

    /** A resolved scope: the grain plus at most one file and one dimension equality. */
    public record Scope(EntityMapping mapping, String fileId, String dim, String dimValue) {
    }

    /**
     * One page of business rows for a scope, filtered. Provenance ({@code elementKey},
     * {@code fileId}) and the transaction's dimensions are merged onto every row, so a claim
     * carries its payer without the caller joining anything.
     */
    public record Page(List<Map<String, Object>> rows, long total) {
    }

    public Page page(Scope scope, java.util.function.Predicate<Map<String, Object>> filter,
            int pageSize, int pageNumber) throws SQLException {
        return page(scope, filter, null, null, pageSize, pageNumber);
    }

    /**
     * One page, optionally filtered and sorted.
     *
     * <p>Sorting is by the column's DECLARED type — an amount compares as an exact decimal, a
     * date as an ISO string, text case-insensitively — and NULLs sort last in both directions,
     * so a page is never led by rows missing the field it was sorted on.
     *
     * <p>A sort, like a filter, forces the scoped set to be read and projected before paging: a
     * business column can sit behind a qualifier predicate or inside a composite, so SQLite
     * cannot order by it. Bounded by the segment, not the buffer (DESIGN §8.5.1).
     */
    public Page page(Scope scope, java.util.function.Predicate<Map<String, Object>> filter,
            String sortBy, String sortDir, int pageSize, int pageNumber) throws SQLException {
        final EntityMapping m = scope.mapping();
        final int offset = Math.max(0, pageNumber - 1) * pageSize;
        final java.util.Comparator<Map<String, Object>> order = comparator(m, sortBy, sortDir);

        if (m.isDimensionGrain()) {
            return slice(parties(m), filter, order, pageSize, offset);
        }

        if (filter == null && order == null) {
            // Unfiltered: the scope IS the page, so grain + segment + paging all happen in SQL.
            final List<Map<String, Object>> rows = project(m,
                buffer.anchorKeys(m.anchorSchemaId(), scope.fileId(), scope.dim(), scope.dimValue(),
                    pageSize, offset));
            return new Page(rows, buffer.anchorCount(m.anchorSchemaId(), scope.fileId(), scope.dim(),
                scope.dimValue()));
        }

        // Filtered and/or sorted: read the scoped set, project, then apply and page.
        final List<Map<String, Object>> all = project(m,
            buffer.anchorKeys(m.anchorSchemaId(), scope.fileId(), scope.dim(), scope.dimValue(),
                Integer.MAX_VALUE, 0));
        return slice(all, filter, order, pageSize, offset);
    }

    private static Page slice(List<Map<String, Object>> all,
            java.util.function.Predicate<Map<String, Object>> filter,
            java.util.Comparator<Map<String, Object>> order, int pageSize, int offset) {
        final List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> row : all) {
            if (filter == null || filter.test(row)) {
                matched.add(row);
            }
        }
        if (order != null) {
            matched.sort(order);
        }
        final List<Map<String, Object>> page = offset >= matched.size()
            ? List.of()
            : matched.subList(offset, Math.min(matched.size(), offset + pageSize));
        return new Page(List.copyOf(page), matched.size());
    }

    // --- dimension grain -------------------------------------------------------

    /**
     * The rows of a dimension-grain entity: one per distinct party the entity's guides name.
     *
     * <p><b>Identity rule.</b> A party is keyed by its identifier when it has one
     * ({@code id:<payerId>}), because an identifier is stable where a name is not ("EXAMPLE
     * HEALTH PLAN" vs "Example Health Plan Inc"). An occurrence that carries only a name joins
     * the identified party with that same name (trimmed, case-insensitive) when exactly one
     * such party exists — an 835 {@code N1*PR} often omits {@code N104} while the 837
     * {@code NM1*PR} for the same payer carries it. With no identified match, or with several
     * (the name alone cannot say which), it is its own party keyed {@code name:<NAME>}. So an
     * occurrence is never guessed onto a party whose identifier it did not state, and the
     * same payer seen under several guides is one row.
     *
     * <p>A party's name is the one most of its transactions carry (ties to the
     * lexicographically first); counts, first/last receipt and transaction types are exact
     * over the transaction sets currently in the buffer.
     */
    private List<Map<String, Object>> parties(EntityMapping m) throws SQLException {
        final EntityMapping.Identity identity = m.identity();
        final List<BufferStore.PartyOccurrence> occurrences = new ArrayList<>();
        for (BufferStore.PartyOccurrence o : buffer.partyOccurrences(identity.nameDimension(),
                identity.idDimension())) {
            final String canonical = com.zerobias.module.x12.parser.TransactionTypes.canonical(o.gs08())
                .orElse(o.gs08());
            if (m.guides().contains(canonical)) {
                occurrences.add(o);
            }
        }

        // identified parties first, so name-only occurrences have something to join
        final Map<String, Party> byKey = new LinkedHashMap<>();
        final Map<String, List<String>> idKeysByName = new LinkedHashMap<>();
        for (BufferStore.PartyOccurrence o : occurrences) {
            final String id = blankToNull(o.id());
            if (id == null) {
                continue;
            }
            final String key = "id:" + id;
            byKey.computeIfAbsent(key, k -> new Party(k, id)).add(o);
        }
        for (Party p : byKey.values()) {
            for (String n : p.names.keySet()) {
                idKeysByName.computeIfAbsent(normalName(n), k -> new ArrayList<>()).add(p.key);
            }
        }
        for (BufferStore.PartyOccurrence o : occurrences) {
            if (blankToNull(o.id()) != null) {
                continue;
            }
            final String normal = normalName(o.name());
            final List<String> candidates = idKeysByName.getOrDefault(normal, List.of());
            final String key = candidates.size() == 1 ? candidates.get(0) : "name:" + normal;
            byKey.computeIfAbsent(key, k -> new Party(k, null)).add(o);
        }

        final List<Map<String, Object>> rows = new ArrayList<>();
        for (Party p : byKey.values()) {
            final Map<String, Object> row = new LinkedHashMap<>();
            for (EntityMapping.Column c : m.columns()) {
                row.put(c.name(), p.field(c.path()));
            }
            rows.add(row);
        }
        return rows;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String normalName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
    }

    /** One party being folded together from its occurrences. */
    private static final class Party {
        final String key;
        final String id;
        final Map<String, Long> names = new LinkedHashMap<>();
        final java.util.TreeSet<String> types = new java.util.TreeSet<>();
        long count;
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;

        Party(String key, String id) {
            this.key = key;
            this.id = id;
        }

        void add(BufferStore.PartyOccurrence o) {
            final String name = blankToNull(o.name());
            if (name != null) {
                names.merge(name, o.transactionCount(), Long::sum);
            }
            if (o.transactionType() != null) {
                types.add(o.transactionType());
            }
            count += o.transactionCount();
            first = Math.min(first, o.firstReceivedAt());
            last = Math.max(last, o.lastReceivedAt());
        }

        String name() {
            String best = null;
            long bestCount = -1;
            for (Map.Entry<String, Long> e : names.entrySet()) {
                if (e.getValue() > bestCount || (e.getValue() == bestCount && e.getKey().compareTo(best) < 0)) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }
            return best;
        }

        Object field(String field) {
            switch (field) {
                case "key":
                    return key;
                case "name":
                    return name();
                case "id":
                    return id;
                case "transactionCount":
                    return count;
                case "firstSeen":
                    return count == 0 ? null : java.time.Instant.ofEpochMilli(first).toString();
                case "lastSeen":
                    return count == 0 ? null : java.time.Instant.ofEpochMilli(last).toString();
                case "transactionTypes":
                    return String.join(",", types);
                default:
                    return null;
            }
        }
    }

    /**
     * A comparator for {@code sortBy}, or null when no sort was asked for.
     *
     * @throws IllegalArgumentException for an unknown attribute or direction — a sort on a
     *     field that does not exist is a mistake, not an arbitrary order
     */
    java.util.Comparator<Map<String, Object>> comparator(EntityMapping mapping, String sortBy,
            String sortDir) {
        if (sortBy == null || sortBy.isBlank()) {
            return null;
        }
        final String attribute = sortBy.trim();
        final String type = mapping.typeOf(attribute);
        if (type == null) {
            throw new IllegalArgumentException("unknown sort attribute '" + attribute + "' on "
                + mapping.name() + "; available: " + mapping.attributes().keySet());
        }
        final boolean descending = "DESC".equals(
            com.zerobias.module.x12.filter.X12Filter.direction(sortDir));

        java.util.Comparator<Map<String, Object>> cmp = (a, b) -> {
            final Object x = a.get(attribute);
            final Object y = b.get(attribute);
            if (x == null || y == null) {
                return x == null && y == null ? 0 : (x == null ? 1 : -1);   // NULLs last, always
            }
            if ("decimal".equals(type) || "integer".equals(type)) {
                return decimal(x).compareTo(decimal(y));
            }
            return String.valueOf(x).compareToIgnoreCase(String.valueOf(y));
        };
        if (descending) {
            // reverse the values but keep NULLs last rather than flipping them to the front
            final java.util.Comparator<Map<String, Object>> asc = cmp;
            cmp = (a, b) -> {
                final boolean an = a.get(attribute) == null;
                final boolean bn = b.get(attribute) == null;
                if (an || bn) {
                    return an && bn ? 0 : (an ? 1 : -1);
                }
                return -asc.compare(a, b);
            };
        }
        return cmp;
    }

    private static java.math.BigDecimal decimal(Object raw) {
        if (raw instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) raw;
        }
        if (raw instanceof Number) {
            return new java.math.BigDecimal(raw.toString());
        }
        try {
            return new java.math.BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException notANumber) {
            return java.math.BigDecimal.ZERO;
        }
    }

    /** Project the anchors identified by {@code element_key + path} keys. */
    private List<Map<String, Object>> project(EntityMapping m, List<String> anchorKeys)
            throws SQLException {
        final List<Map<String, Object>> rows = new ArrayList<>(anchorKeys.size());
        final Map<String, List<EntityGraph.Entity>> graphCache = new LinkedHashMap<>();
        final Map<String, Map<String, EntityGraph.Value>> dimCache = new LinkedHashMap<>();

        for (String key : anchorKeys) {
            final String[] parts = key.split("\u001f", -1);
            if (parts.length < 3) {
                continue;
            }
            final String elementKey = parts[0];
            final String path = parts[1];
            final String fileId = parts[2];

            // One graph read serves every anchor of the same transaction set.
            final List<EntityGraph.Entity> graph = graphCache.computeIfAbsent(elementKey, k -> {
                try {
                    return buffer.graphFor(k);
                } catch (SQLException e) {
                    throw new IllegalStateException("cannot read graph for " + k, e);
                }
            });
            EntityGraph.Entity anchor = null;
            for (EntityGraph.Entity e : graph) {
                if (path.equals(e.path) && m.anchorSchemaId().equals(e.schemaId)) {
                    anchor = e;
                    break;
                }
            }
            if (anchor == null) {
                continue;
            }
            final Map<String, Object> row = m.project(graph, anchor);
            row.put("elementKey", elementKey);
            row.put("fileId", fileId);
            final Map<String, EntityGraph.Value> dims = dimCache.computeIfAbsent(elementKey, k -> {
                try {
                    return buffer.dimsFor(k);
                } catch (SQLException e) {
                    throw new IllegalStateException("cannot read dimensions for " + k, e);
                }
            });
            for (EntityMapping.Dimension d : m.dimensions()) {
                final EntityGraph.Value v = dims.get(d.name());
                // typed like a column: a decimal dimension is a number, not its text
                row.put(d.name(), EntityMapping.typed(d.dataType(), v));
            }
            rows.add(row);
        }
        return rows;
    }
}
