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
        for (EntityMapping m : mappings == null ? List.<EntityMapping>of() : mappings) {
            byCollection.putIfAbsent(m.collection(), m);
        }
    }

    /** Every bundled guide's mappings, in guide order. */
    public static List<EntityMapping> mappingsFor(List<String> guides) {
        final List<EntityMapping> out = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (String gs08 : guides == null ? List.<String>of() : guides) {
            for (EntityMapping m : EntityMapping.forGuide(gs08)) {
                if (seen.add(m.collection())) {
                    out.add(m);
                }
            }
        }
        return out;
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
        if (FILE_SEGMENT.equals(segment)) {
            return buffer.distinctAnchorFiles(m.anchorSchemaId());
        }
        return buffer.distinctDim(segment);
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
        final EntityMapping m = scope.mapping();
        final int offset = Math.max(0, pageNumber - 1) * pageSize;

        if (filter == null) {
            // Unfiltered: the scope IS the page, so grain + segment + paging all happen in SQL.
            final List<Map<String, Object>> rows = project(m,
                buffer.anchorKeys(m.anchorSchemaId(), scope.fileId(), scope.dim(), scope.dimValue(),
                    pageSize, offset));
            return new Page(rows, buffer.anchorCount(m.anchorSchemaId(), scope.fileId(), scope.dim(),
                scope.dimValue()));
        }

        // Filtered: read the scoped set, project, then filter and page. Bounded by the segment.
        final List<Map<String, Object>> all = project(m,
            buffer.anchorKeys(m.anchorSchemaId(), scope.fileId(), scope.dim(), scope.dimValue(),
                Integer.MAX_VALUE, 0));
        final List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> row : all) {
            if (filter.test(row)) {
                matched.add(row);
            }
        }
        final List<Map<String, Object>> page = offset >= matched.size()
            ? List.of()
            : matched.subList(offset, Math.min(matched.size(), offset + pageSize));
        return new Page(List.copyOf(page), matched.size());
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
                row.put(d.name(), v == null ? null : v.text());
            }
            rows.add(row);
        }
        return rows;
    }
}
