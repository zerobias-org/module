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
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

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
 *       the {@link RecastHook}.</li>
 *   <li>{@code purge} — delete acked rows older than a duration (default: all acked).</li>
 *   <li>{@code raw} — the stored ST..SE segments verbatim (+ ISA/GS context) for one row.</li>
 *   <li>{@code validate} — {@code stored} verdict (schema registered, typed JSON parses,
 *       envelope complete) and the {@code rematerialized} verdict from the stored raw.</li>
 *   <li>{@code rescan} — force an immediate poll through the {@link PollerHandle}.</li>
 * </ul>
 *
 * <p>Every input is checked against the function's declared input schema
 * ({@link SchemaRegistry#functionInputs}) before anything runs: an unknown key, a wrong
 * type, a missing required property or an unparseable filter/duration is a 400, never a
 * silently-dropped argument — {@code purge {"olderthan": "P30D"}} must not purge every acked
 * row. {@link #validateInput} runs the same check without executing.
 *
 * <p>The only function-specific error is {@code not_found} ({@link #declaredErrors}): an
 * unknown {@code elementKey} ({@code raw}, {@code validate}), source ({@code rescan}) or
 * lease ({@code ack}, {@code release}).
 */
public final class X12Operations {

    static final int DEFAULT_MAX = 100;
    static final int MAX_CAP = 1000;
    static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private static final Gson GSON = new Gson();

    private final BufferStore buffer;
    private final PollerHandle pollers;
    private final SchemaRegistry schemas;
    private final RecastHook recaster;
    /** Lazily read once: the bundled catalog is classpath-immutable for the process's life. */
    private PackCatalog packCatalog;

    public X12Operations(BufferStore buffer, PollerHandle pollers, SchemaRegistry schemas, RecastHook recaster) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.pollers = Objects.requireNonNull(pollers, "pollers");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.recaster = Objects.requireNonNull(recaster, "recaster");
    }

    /** The {@code throws} map of {@code /ops/<fn>}: error code → schema of the body raised for it. */
    static Map<String, String> declaredErrors(String fn) {
        switch (fn) {
            case "ack":
            case "release":
            case "raw":
            case "validate":
            case "rescan":
                return Map.of("not_found", SchemaRegistry.NOT_FOUND_ERROR_SCHEMA);
            default:
                return Map.of();
        }
    }

    /**
     * Dispatch a {@code /x12-receiver/ops/<fn>} invocation; the map is serialized as the
     * function output. Unknown {@code fn} → {@code noSuchObjectError}; input that fails its
     * schema → {@code illegalArgumentError} and nothing runs.
     */
    public Map<String, Object> invoke(String fn, Map<String, Object> input) throws SQLException {
        requireFunction(fn);
        Map<String, Object> in = input == null ? Map.of() : input;
        List<Issue> errors = check(fn, in).errors();
        if (!errors.isEmpty()) {
            StringJoiner msg = new StringJoiner("; ", "Invalid input for " + fn + ": ", "");
            errors.forEach(e -> msg.add(e.path().isEmpty() ? e.message() : e.path() + ": " + e.message()));
            throw ProducerException.illegalArgument(msg.toString());
        }
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
            default:
                return rescan(in);
        }
    }

    /**
     * {@code validateFunctionInput}: the interface {@code ValidationResult} for {@code input}
     * — the same check {@link #invoke} applies, so {@code valid} means invoke will accept it.
     * Warnings flag input that runs but is adjusted (a {@code max} or {@code leaseTtl} above
     * its cap); in {@code strict} mode they count as errors.
     */
    public Map<String, Object> validateInput(String fn, Object input, boolean strict) {
        requireFunction(fn);
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        if (input != null && !(input instanceof Map)) {
            errors.add(new Issue("", "input must be a JSON object", "type"));
        } else {
            @SuppressWarnings("unchecked")
            Check c = check(fn, input == null ? Map.of() : (Map<String, Object>) input);
            errors.addAll(c.errors());
            warnings.addAll(c.warnings());
        }
        if (strict) {
            errors.addAll(warnings);
            warnings.clear();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valid", errors.isEmpty());
        out.put("errors", issues(errors, true));
        out.put("warnings", issues(warnings, false));
        return out;
    }

    private static void requireFunction(String fn) {
        if (!SchemaRegistry.OPS_FUNCTIONS.contains(fn)) {
            throw ProducerException.noSuchObject(ObjectTree.RECEIVER + "/ops/" + fn);
        }
    }

    // --- input checking ------------------------------------------------------

    /** One problem in a function input: the property ({@code ""} = the input itself), what, and a code. */
    record Issue(String path, String message, String code) {
    }

    private record Check(List<Issue> errors, List<Issue> warnings) {
    }

    /** Schema check (keys, types, required) then the per-property value rules. */
    private static Check check(String fn, Map<String, Object> input) {
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        List<SchemaRegistry.Param> params = SchemaRegistry.functionInputs(fn);
        List<String> names = new ArrayList<>();
        params.forEach(p -> names.add(p.name()));
        for (String key : input.keySet()) {
            if (!names.contains(key)) {
                errors.add(new Issue(key, "unknown property (expected "
                    + (names.isEmpty() ? "none" : String.join(", ", names)) + ")", "unknown_property"));
            }
        }
        for (SchemaRegistry.Param p : params) {
            Object v = input.get(p.name());
            if (v == null) {
                if (p.required()) {
                    errors.add(new Issue(p.name(), "is required", "required"));
                }
                continue;
            }
            if (p.multi()) {
                if (!(v instanceof List)) {
                    errors.add(new Issue(p.name(), "must be an array of " + p.dataType(), "type"));
                    continue;
                }
                List<?> items = (List<?>) v;
                for (int i = 0; i < items.size(); i++) {
                    if (!hasType(items.get(i), p.dataType())) {
                        errors.add(new Issue(p.name() + "[" + i + "]", "must be a " + p.dataType(), "type"));
                    }
                }
            } else if (!hasType(v, p.dataType())) {
                errors.add(new Issue(p.name(), "must be a" + ("integer".equals(p.dataType()) ? "n " : " ")
                    + p.dataType(), "type"));
            } else {
                checkValue(p.name(), v, errors, warnings);
            }
        }
        if (names.contains("elementKeys")) {
            checkElementKeys(input.get("elementKeys"), errors);
        }
        return new Check(errors, warnings);
    }

    private static boolean hasType(Object v, String dataType) {
        switch (dataType) {
            case "integer":
                if (v instanceof Integer || v instanceof Long || v instanceof Short) {
                    return ((Number) v).longValue() == ((Number) v).intValue();
                }
                if (v instanceof Double || v instanceof Float) {
                    double d = ((Number) v).doubleValue();
                    return d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE;
                }
                return false;
            case "boolean":
                return v instanceof Boolean;
            default:
                return v instanceof String;
        }
    }

    /** Value rules beyond the type, for properties whose type already checked out. */
    private static void checkValue(String name, Object v, List<Issue> errors, List<Issue> warnings) {
        switch (name) {
            case "filter":
                try {
                    renderFilter((String) v);
                } catch (ProducerException e) {
                    errors.add(new Issue(name, e.getMessage(), "malformed_filter"));
                }
                break;
            case "max":
                int max = ((Number) v).intValue();
                if (max < 1) {
                    errors.add(new Issue(name, "must be at least 1", "out_of_range"));
                } else if (max > MAX_CAP) {
                    warnings.add(new Issue(name, "is capped at " + MAX_CAP, "capped"));
                }
                break;
            case "leaseTtl":
                if (checkDuration(name, (String) v, false, errors)
                        && Duration.parse((String) v).compareTo(BufferStore.MAX_LEASE_TTL) > 0) {
                    warnings.add(new Issue(name, "is capped at " + BufferStore.MAX_LEASE_TTL, "capped"));
                }
                break;
            case "olderThan":
                checkDuration(name, (String) v, true, errors);
                break;
            case "leaseId":
            case "elementKey":
                if (((String) v).isBlank()) {
                    errors.add(new Issue(name, "must not be blank", "required"));
                }
                break;
            default:
                break;
        }
    }

    /** True when {@code raw} is a duration in range; otherwise the problem is added to {@code errors}. */
    private static boolean checkDuration(String name, String raw, boolean zeroAllowed, List<Issue> errors) {
        try {
            Duration d = Duration.parse(raw);
            if (d.isNegative() || (!zeroAllowed && d.isZero())) {
                errors.add(new Issue(name, zeroAllowed ? "must not be negative" : "must be positive", "out_of_range"));
                return false;
            }
            return true;
        } catch (DateTimeParseException e) {
            errors.add(new Issue(name, "must be an ISO-8601 duration (e.g. PT5M): " + raw, "invalid_duration"));
            return false;
        }
    }

    /** An empty subset would mean "the whole lease" to the buffer — never what a caller listing keys meant. */
    private static void checkElementKeys(Object v, List<Issue> errors) {
        if (v instanceof List && ((List<?>) v).isEmpty()) {
            errors.add(new Issue("elementKeys", "must not be empty; omit it to cover the whole lease", "out_of_range"));
        }
    }

    private static List<Map<String, Object>> issues(List<Issue> list, boolean withCode) {
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Issue i : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("path", i.path());
            m.put("message", i.message());
            if (withCode) {
                m.put("code", i.code());
            }
            out.add(m);
        }
        return out;
    }

    // --- drain ---------------------------------------------------------------

    private Map<String, Object> take(Map<String, Object> input) throws SQLException {
        int max = Math.min(intArg(input, "max", DEFAULT_MAX), MAX_CAP);
        Duration ttl = durationArg(input, "leaseTtl", DEFAULT_TTL);
        String where = renderFilter(strArg(input, "filter"));

        Lease lease = buffer.takeWhere(where, max, ttl);
        List<Map<String, Object>> transactions = new ArrayList<>(lease.transactions().size());
        for (TransactionRow r : lease.transactions()) {
            transactions.add(X12ProducerFacade.toElement(r));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leaseId", lease.leaseId());      // null when nothing was drainable (serializeNulls)
        out.put("transactions", transactions);
        out.put("remaining", lease.remaining());
        return out;
    }

    private Map<String, Object> ack(Map<String, Object> input) throws SQLException {
        String leaseId = strArg(input, "leaseId");
        int acked = buffer.ack(leaseId, elementKeys(input));
        if (acked == 0) {
            requireLease(leaseId);
        }
        return Map.of("acked", acked);
    }

    private Map<String, Object> release(Map<String, Object> input) throws SQLException {
        String leaseId = strArg(input, "leaseId");
        int released = buffer.release(leaseId, elementKeys(input));
        if (released == 0) {
            requireLease(leaseId);
        }
        return Map.of("released", released);
    }

    /**
     * 404 when no in-flight row carries {@code leaseId}. Checked only after a finalize
     * touched nothing: 0 against a live lease is a subset naming keys outside it, reported
     * as a count, not an error.
     */
    private void requireLease(String leaseId) throws SQLException {
        if (!buffer.exists("lease_id = " + ObjectTree.sql(leaseId) + " AND status = 'in_flight'")) {
            throw ProducerException.noSuchLease(leaseId);
        }
    }

    private Map<String, Object> replay(Map<String, Object> input) throws SQLException {
        int replayed = buffer.replayInFlight(renderFilter(strArg(input, "filter")));
        return Map.of("replayed", replayed);
    }

    /**
     * Re-materialize stored rows from their raw X12 under the currently-loaded definitions,
     * rewriting {@code mapped_json}/{@code schema_id} only where the result differs. Leased
     * ({@code in_flight}) rows are excluded; per-row failures are counted, never fatal; at
     * most {@code max} rows (newest first) per call.
     */
    private Map<String, Object> recast(Map<String, Object> input) throws SQLException {
        int max = Math.min(intArg(input, "max", MAX_CAP), MAX_CAP);
        String where = renderFilter(strArg(input, "filter"));

        List<TransactionRow> rows = buffer.recastable(where, max);
        int recast = 0;
        int unchanged = 0;
        int failed = 0;
        for (TransactionRow row : rows) {
            try {
                Optional<RecastHook.Mapping> m = recaster.recast(row);
                if (m.isPresent() && buffer.updateMapping(row.id(), m.get().schemaId(), m.get().mappedJson())) {
                    recast++;
                } else {
                    // empty = reproduced the stored value; update==false = row got leased
                    // between select and write (skipped). Either way, nothing rewritten.
                    unchanged++;
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
     * Validate one buffered row. {@code stored} checks the row as buffered: its
     * {@code schemaId} is registered, {@code mapped_json} parses as a JSON object, and the
     * envelope columns are complete. {@code rematerialized} applies the same checks to the
     * form re-derived from the stored raw, and {@code repsAgree} says whether the two are
     * byte-identical. {@code parserErrors} are the non-fatal imsweb errors the re-parse
     * reported; {@code parserErrorCount} is the count recorded at ingest.
     */
    private Map<String, Object> validate(Map<String, Object> input) throws SQLException {
        TransactionRow row = requireRow(input);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("elementKey", row.elementKey());
        out.put("schemaId", row.schemaId());
        out.put("stored", storedVerdict(row));
        List<String> parserErrors = List.of();
        try {
            RecastHook.Mapping m = recaster.rematerialize(row);
            Map<String, Object> rv = storedVerdict(row.withMapping(m.schemaId(), m.mappedJson()));
            rv.put("schemaId", m.schemaId());
            out.put("rematerialized", rv);
            out.put("repsAgree", m.reproduces(row));
            parserErrors = m.parserErrors();
        } catch (Exception e) {
            Map<String, Object> rv = new LinkedHashMap<>();
            rv.put("valid", false);
            rv.put("errors", List.of("re-materialization failed: " + e.getMessage()));
            rv.put("schemaId", null);   // always present, as on success; null: nothing was re-derived
            out.put("rematerialized", rv);
            out.put("repsAgree", false);
        }
        out.put("parserErrors", parserErrors);
        out.put("parserErrorCount", row.parserErrorCount());
        return out;
    }

    /** {@code {valid, errors[]}} for the stored representation of {@code row}. */
    private Map<String, Object> storedVerdict(TransactionRow row) {
        List<String> errors = new ArrayList<>();
        if (row.schemaId() == null || row.schemaId().isBlank()) {
            errors.add("schemaId is missing");
        } else if (!schemas.has(row.schemaId())) {
            errors.add("schema not registered: " + row.schemaId());
        }
        if (row.mappedJson() == null || row.mappedJson().isBlank()) {
            errors.add("mapped_json is empty");
        } else {
            try {
                JsonObject body = GSON.fromJson(row.mappedJson(), JsonObject.class);
                if (body == null) {
                    errors.add("mapped_json is not a JSON object");
                }
            } catch (JsonSyntaxException | IllegalStateException e) {
                errors.add("mapped_json does not parse: " + e.getMessage());
            }
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
     * every source. 404 when the named source isn't watched. A scan that fails is a 500:
     * the cause (a buffer or filesystem error) is logged by the HTTP layer, not echoed.
     */
    private Map<String, Object> rescan(Map<String, Object> input) {
        String source = strArg(input, "source");
        if (source != null && source.isBlank()) {
            source = null;
        }
        if (source != null) {
            boolean known = false;
            for (PollerStatus.SourceStatus s : pollers.sources()) {
                if (source.equals(s.name())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw ProducerException.noSuchObject(ObjectTree.BY_SOURCE + "/" + ObjectTree.encodeSegment(source));
            }
        }
        PollerHandle.RescanResult r;
        try {
            r = pollers.rescan(source);
        } catch (Exception e) {
            throw new IllegalStateException("rescan failed", e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scanned", r.scanned());
        out.put("discovered", r.discovered());
        out.put("consumed", r.consumed());
        out.put("errored", r.errored());
        return out;
    }

    // --- input parsing (after check(): types and values are known good) --------

    /** The single buffered row for {@code elementKey} (the DataProducer key), or 404. */
    private TransactionRow requireRow(Map<String, Object> input) throws SQLException {
        String key = strArg(input, "elementKey");
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

    @SuppressWarnings("unchecked")
    private static List<String> elementKeys(Map<String, Object> input) {
        Object v = input.get("elementKeys");
        return v == null ? null : List.copyOf((List<String>) v);   // null = the whole lease
    }

    private static String strArg(Map<String, Object> input, String key) {
        Object v = input.get(key);
        return v == null ? null : v.toString();
    }

    private static int intArg(Map<String, Object> input, String key, int dflt) {
        Object v = input.get(key);
        return v == null ? dflt : ((Number) v).intValue();
    }

    private static Duration durationArg(Map<String, Object> input, String key, Duration dflt) {
        String raw = strArg(input, key);
        return raw == null ? dflt : Duration.parse(raw);
    }
}
