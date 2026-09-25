package com.zerobias.module.x12.producer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.zerobias.module.x12.PollerHandle;
import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.buffer.Lease;
import com.zerobias.module.x12.buffer.TransactionRow;
import com.zerobias.module.x12.filter.X12Filter;
import com.zerobias.module.x12.health.PollerStatus;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Function objects under {@code /x12-receiver/ops/*} (DESIGN §2.5), invoked via
 * {@code invokeFunction}. Thin I/O-shaping wrappers over the buffer's lease primitives,
 * mirroring hl7/v2's {@code Hl7Operations}:
 *
 * <ul>
 *   <li>{@code take} — lease drainable rows ({@code new} or TTL-expired {@code in_flight}),
 *       optionally narrowed by an RFC4515 {@code filter}; returns {@code leaseId} (null
 *       when nothing was drainable), the {@code transactions} (envelope + typed body) and
 *       the approximate {@code remaining} backlog.</li>
 *   <li>{@code ack} / {@code release} — finalize / return a lease; optional
 *       {@code elementKeys} subset (partial acks; un-acked rows revert at TTL).</li>
 *   <li>{@code replay} — force in_flight rows back to {@code new}, optionally filtered.</li>
 *   <li>{@code recast} — re-materialize rows from stored raw under current definitions via
 *       the {@link RecastHook} seam; without a materializer nothing is rewritten.</li>
 *   <li>{@code purge} — delete acked rows older than a duration (default: all acked).</li>
 *   <li>{@code raw} — the stored ST..SE segments verbatim (+ ISA/GS context) for one row.</li>
 *   <li>{@code validate} — {@code stored} verdict (schema registered, typed JSON parses,
 *       envelope complete) and, through the seam, the {@code rematerialized} verdict.</li>
 *   <li>{@code rescan} — force an immediate poll through the {@link PollerHandle}.</li>
 * </ul>
 *
 * <p>The declared {@code throws} codes ({@code lease_capacity_exceeded}, {@code backpressure},
 * {@code lease_expired}) are on the objects but not raised in v1: there is no outstanding-lease
 * cap, backpressure is applied on the inbox path (files left untouched), and a finalized or
 * expired lease has no {@code in_flight} rows left — {@code ack}/{@code release} report the
 * affected count and 0 is the signal (the buffer clears {@code lease_id} on finalize, so an
 * unknown and an already-finalized lease are indistinguishable).
 */
public final class X12Operations implements OperationsApi {

    private static final Gson GSON = new Gson();

    private final BufferStore buffer;
    private final java.util.function.BiFunction<TransactionRow, Map<String, Object>, Map<String, Object>> elementMapper;
    private final Supplier<PollerHandle> pollers;
    private final SchemaRegistryApi schemas;
    private final RecastHook recaster;
    /** Lazily read once: the bundled catalog is classpath-immutable for the process's life. */
    private PackCatalog packCatalog;

    public X12Operations(BufferStore buffer, Supplier<PollerHandle> pollers, SchemaRegistryApi schemas) {
        this(buffer, X12ProducerFacade::toElement, pollers, schemas, RecastHook.NONE);
    }

    public X12Operations(BufferStore buffer,
            java.util.function.BiFunction<TransactionRow, Map<String, Object>, Map<String, Object>> elementMapper,
            Supplier<PollerHandle> pollers, SchemaRegistryApi schemas, RecastHook recaster) {
        this.buffer = buffer;
        this.elementMapper = elementMapper == null ? X12ProducerFacade::toElement : elementMapper;
        this.pollers = pollers == null ? () -> null : pollers;
        this.schemas = schemas == null ? SchemaRegistryApi.EMPTY : schemas;
        this.recaster = recaster == null ? RecastHook.NONE : recaster;
    }

    @Override
    public Map<String, Object> invoke(String fn, Map<String, Object> input) throws SQLException {
        Map<String, Object> in = input == null ? Map.of() : input;
        switch (fn) {
            case "take":
                return take(in);
            case "ack":
                return ack(in);
            case "release":
                return release(in);
            case "replay":
                return replay(in);
            case "recast":
                return recast(in);
            case "purge":
                return purge(in);
            case "raw":
                return raw(in);
            case "validate":
                return validate(in);
            case "rescan":
                return rescan(in);
            case "packs":
                return packs(in);
            default:
                throw ProducerException.noSuchObject(ObjectTreeApi.RECEIVER + "/ops/" + fn);
        }
    }

    // --- content packs (DESIGN §7) -------------------------------------------

    /**
     * What content this deployment has and where it came from: the bundled packs from
     * {@code packs.json}, each reported with the schema ids it declares and whether the
     * registry can actually serve them ({@code status: active|degraded}).
     *
     * <p>Read-only by design. Installing a delivered pack needs a delivery path first
     * (npm at image build, or upload + validate); this op is what makes the bundled floor
     * discoverable, so a caller can tell a stock deployment from an extended one without
     * reading container logs.
     *
     * <p>{@code name} / {@code gs08} narrow the report; an unknown value is not an error,
     * it is an empty list, because "is this pack present?" is exactly the question being
     * asked.
     */
    private Map<String, Object> packs(Map<String, Object> input) {
        final PackCatalog catalog = catalog();
        final String name = strArg(input, "name");
        final String gs08 = strArg(input, "gs08");

        final List<Map<String, Object>> described = new ArrayList<>();
        for (PackCatalog.Pack p : catalog.packs()) {
            if (name != null && !name.equals(p.name())) {
                continue;
            }
            if (gs08 != null && !gs08.equals(p.gs08())) {
                continue;
            }
            described.add(p.describe(schemas));
        }

        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("packCount", described.size());
        int declared = 0;
        for (Map<String, Object> d : described) {
            declared += (Integer) d.get("schemaCount");
        }
        out.put("schemaCount", declared);
        out.put("registrySize", schemas instanceof SchemaRegistry ? ((SchemaRegistry) schemas).size() : -1);
        out.put("guides", catalog.guides());
        out.put("packs", described);
        return out;
    }

    private PackCatalog catalog() {
        if (packCatalog == null) {
            packCatalog = PackCatalog.fromClasspath();
        }
        return packCatalog;
    }

    // --- drain ---------------------------------------------------------------

    private Map<String, Object> take(Map<String, Object> input) throws SQLException {
        int max = clampMax(intArg(input, "max", DEFAULT_MAX));
        Duration ttl = durationArg(input, "leaseTtl", DEFAULT_TTL);
        String where = renderFilter(strArg(input, "filter"));

        Lease lease = buffer.takeWhere(where, max, ttl);
        // Reassembled from the object graph, batched for the whole lease (DESIGN §8.4).
        final Map<String, Map<String, Object>> bodies = buffer.documentsFor(
            lease.transactions().stream().map(TransactionRow::elementKey).toList());
        List<Map<String, Object>> transactions = new ArrayList<>(lease.transactions().size());
        for (TransactionRow r : lease.transactions()) {
            transactions.add(elementMapper.apply(r, bodies.get(r.elementKey())));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leaseId", lease.leaseId());      // null when nothing was drainable (serializeNulls)
        out.put("transactions", transactions);
        out.put("remaining", lease.remaining());
        return out;
    }

    private Map<String, Object> ack(Map<String, Object> input) throws SQLException {
        String leaseId = requireLeaseId(input);
        int acked = buffer.ack(leaseId, elementKeys(input));
        return Map.of("acked", acked);
    }

    private Map<String, Object> release(Map<String, Object> input) throws SQLException {
        String leaseId = requireLeaseId(input);
        int released = buffer.release(leaseId, elementKeys(input));
        return Map.of("released", released);
    }

    private Map<String, Object> replay(Map<String, Object> input) throws SQLException {
        int replayed = buffer.replayInFlight(renderFilter(strArg(input, "filter")));
        return Map.of("replayed", replayed);
    }

    /**
     * Re-materialize stored rows from their raw X12 under the currently-loaded definitions,
     * rewriting {@code mapped_json}/{@code schema_id} only where the result differs. Leased
     * ({@code in_flight}) rows are excluded; per-row failures are counted, never fatal; at
     * most {@code max} rows (newest first) per call. Without a materializer behind the
     * {@link RecastHook} seam every examined row is reported {@code unchanged} plus a {@code note}.
     */
    private Map<String, Object> recast(Map<String, Object> input) throws SQLException {
        int max = clampMax(intArg(input, "max", MAX_CAP));
        String where = renderFilter(strArg(input, "filter"));

        List<TransactionRow> rows = buffer.recastable(where, max);
        int recast = 0;
        int unchanged = 0;
        int failed = 0;
        for (TransactionRow row : rows) {
            try {
                Optional<RecastHook.Mapping> m = recaster.recast(row);
                if (m.isEmpty() || recaster.reproduces(m.get(), row, buffer.documentFor(row.elementKey()))) {
                    unchanged++;   // the current definitions reproduce what is stored
                } else if (buffer.replaceGraph(row, m.get().schemaId(), m.get().graph())) {
                    recast++;
                } else {
                    unchanged++;   // leased between select and write; never rewritten under a consumer
                }
            } catch (Exception e) {
                failed++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("examined", rows.size());
        out.put("recast", recast);
        out.put("unchanged", unchanged);
        out.put("failed", failed);
        if (!recaster.available()) {
            out.put("note", "recast requires the materializer");
        }
        return out;
    }

    private Map<String, Object> purge(Map<String, Object> input) throws SQLException {
        // Default Duration.ZERO = delete all acked rows (acked before "now").
        Duration olderThan = durationArg(input, "olderThan", Duration.ZERO);
        int purged = buffer.purge(olderThan);
        return Map.of("purged", purged);
    }

    // --- inspection -----------------------------------------------------------

    /**
     * The canonical raw X12 for one buffered transaction set, by {@code elementKey}: the
     * ST..SE segments verbatim plus the ISA/GS context lines, as stored (audit /
     * independent re-parse). 404 when the key isn't buffered.
     */
    private Map<String, Object> raw(Map<String, Object> input) throws SQLException {
        TransactionRow row = requireRow(input);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("elementKey", row.elementKey());
        out.put("fileId", row.fileId());
        out.put("gs08", row.gs08());
        out.put("transactionType", row.transactionType());
        out.put("raw", row.rawX12() == null ? null : new String(row.rawX12(), StandardCharsets.UTF_8));
        return out;
    }

    /**
     * Validate one buffered row. {@code stored} checks what the producer can see without a
     * materializer: the row's {@code schemaId} is registered, {@code mapped_json} parses as
     * a JSON object, and the envelope columns are complete. {@code rematerialized} /
     * {@code repsAgree} come through the {@link RecastHook} seam and are null without one.
     * {@code parserErrors} are the non-fatal imsweb errors the re-parse reported (empty
     * without a materializer); {@code parserErrorCount} is the count recorded at ingest.
     */
    private Map<String, Object> validate(Map<String, Object> input) throws SQLException {
        TransactionRow row = requireRow(input);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("elementKey", row.elementKey());
        out.put("schemaId", row.schemaId());
        final Map<String, Object> storedBody = buffer.documentFor(row.elementKey());
        out.put("stored", storedVerdict(row, storedBody));
        List<String> parserErrors = List.of();
        if (recaster.available()) {
            try {
                RecastHook.Mapping m = recaster.rematerialize(row);
                Map<String, Object> rv = storedVerdict(row.withSchemaId(m.schemaId()), m.body());
                rv.put("schemaId", m.schemaId());
                out.put("rematerialized", rv);
                out.put("repsAgree", recaster.reproduces(m, row, storedBody));
                parserErrors = m.parserErrors();
            } catch (Exception e) {
                Map<String, Object> rv = new LinkedHashMap<>();
                rv.put("valid", false);
                rv.put("errors", List.of("re-materialization failed: " + e.getMessage()));
                out.put("rematerialized", rv);
                out.put("repsAgree", false);
            }
        } else {
            // ==== MATERIALIZER SEAM ==== no re-materialization available (RecastHook.NONE)
            out.put("rematerialized", null);
            out.put("repsAgree", null);
        }
        out.put("parserErrors", parserErrors);
        out.put("parserErrorCount", row.parserErrorCount());
        return out;
    }

    /**
     * {@code {valid, errors[]}} for a representation of {@code row}: its schema must be
     * registered and {@code body} must be a non-empty document. An empty body means the graph
     * is missing — a transaction row with no instances is exactly the corruption this reports.
     */
    private Map<String, Object> storedVerdict(TransactionRow row, Map<String, Object> body) {
        List<String> errors = new ArrayList<>();
        if (row.schemaId() == null || row.schemaId().isBlank()) {
            errors.add("schemaId is missing");
        } else if (!schemas.has(row.schemaId())) {
            errors.add("schema not registered: " + row.schemaId());
        }
        if (body == null || body.isEmpty()) {
            errors.add("the object graph for this transaction set is empty");
        }
        requireEnvelope(errors, "elementKey", row.elementKey());
        requireEnvelope(errors, "fileId", row.fileId());
        requireEnvelope(errors, "sourceName", row.sourceName());
        requireEnvelope(errors, "gs08", row.gs08());
        requireEnvelope(errors, "transactionType", row.transactionType());
        requireEnvelope(errors, "gsControlNumber", row.gsControl());
        requireEnvelope(errors, "stControlNumber", row.stControl());
        if (row.receivedAt() == null) {
            errors.add("envelope field missing: receivedAt");
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("valid", errors.isEmpty());
        v.put("errors", errors);
        return v;
    }

    private static void requireEnvelope(List<String> errors, String name, String value) {
        if (value == null || value.isBlank()) {
            errors.add("envelope field missing: " + name);
        }
    }

    /**
     * Force an immediate poll of one source ({@code source} = its configured name) or of
     * every source. 404 when no poller is running or the named source isn't watched.
     */
    private Map<String, Object> rescan(Map<String, Object> input) {
        PollerHandle handle = pollers.get();
        if (handle == null) {
            throw ProducerException.noSuchObject(ObjectTreeApi.RECEIVER + "/ops/rescan (no inbox poller running)");
        }
        String source = strArg(input, "source");
        if (source != null && source.isBlank()) {
            source = null;
        }
        if (source != null) {
            boolean known = false;
            for (PollerStatus.SourceStatus s : handle.sources()) {
                if (source.equals(s.name())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw ProducerException.noSuchObject(ObjectTreeApi.RECEIVER + "/by-source/" + ObjectTree.encodeSegment(source));
            }
        }
        PollerHandle.RescanResult r;
        try {
            r = handle.rescan(source);
        } catch (ProducerException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw ProducerException.illegalArgument(e.getMessage());
        } catch (Exception e) {
            throw new IllegalStateException("rescan failed: " + e.getMessage(), e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scanned", r == null ? 0 : r.scanned());
        out.put("discovered", r == null ? 0 : r.discovered());
        out.put("consumed", r == null ? 0 : r.consumed());
        out.put("errored", r == null ? 0 : r.errored());
        return out;
    }

    // --- input parsing -----------------------------------------------------

    /** The single buffered row for {@code elementKey} (the DataProducer key), or 404. */
    private TransactionRow requireRow(Map<String, Object> input) throws SQLException {
        String key = strArg(input, "elementKey");
        if (key == null || key.isBlank()) {
            throw ProducerException.illegalArgument("elementKey is required");
        }
        Optional<TransactionRow> row = buffer.byElementKey(key);
        if (row.isEmpty()) {
            throw ProducerException.noSuchObject(ObjectTree.TRANSACTIONS + " / " + key);
        }
        return row.get();
    }

    private static String renderFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return null;
        }
        try {
            return X12Filter.toWhereClause(filter);
        } catch (RuntimeException e) {
            throw ProducerException.illegalArgument("Malformed filter: " + e.getMessage());
        }
    }

    private static String requireLeaseId(Map<String, Object> input) {
        String leaseId = strArg(input, "leaseId");
        if (leaseId == null || leaseId.isBlank()) {
            throw ProducerException.illegalArgument("leaseId is required");
        }
        return leaseId;
    }

    @SuppressWarnings("unchecked")
    private static List<String> elementKeys(Map<String, Object> input) {
        Object v = input.get("elementKeys");
        if (v == null) {
            return null;   // full-lease ack/release
        }
        if (v instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object o : (List<Object>) v) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        throw ProducerException.illegalArgument("elementKeys must be an array");
    }

    private static int clampMax(int max) {
        if (max <= 0) {
            return DEFAULT_MAX;
        }
        return Math.min(max, MAX_CAP);
    }

    private static String strArg(Map<String, Object> input, String key) {
        Object v = input.get(key);
        return v == null ? null : v.toString();
    }

    private static int intArg(Map<String, Object> input, String key, int dflt) {
        Object v = input.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
            } catch (NumberFormatException e) {
                throw ProducerException.illegalArgument(key + " must be an integer");
            }
        }
        return dflt;
    }

    private static Duration durationArg(Map<String, Object> input, String key, Duration dflt) {
        String raw = strArg(input, key);
        if (raw == null || raw.isBlank()) {
            return dflt;
        }
        try {
            return Duration.parse(raw);   // ISO-8601, e.g. PT5M
        } catch (DateTimeParseException e) {
            throw ProducerException.illegalArgument(key + " must be an ISO-8601 duration (e.g. PT5M): " + raw);
        }
    }
}
