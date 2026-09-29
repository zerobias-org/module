package com.zerobias.module.x12.producer;

import java.sql.SQLException;
import java.util.Map;

/**
 * <b>To be implemented by {@code X12Operations}</b> (producer phase, DESIGN §12 step 4):
 * the Function objects under {@code /x12-receiver/ops/*} (DESIGN §2.5), invoked via
 * {@code invokeFunction}. Constants inherited from hl7/v2: {@code DEFAULT_MAX=100},
 * {@code MAX_CAP=1000}, {@code DEFAULT_TTL=PT5M}; output is serialized with
 * {@code serializeNulls} (so {@code take.leaseId} is an explicit null on an empty lease).
 *
 * <table>
 * <tr><th>fn</th><th>input</th><th>output</th><th>buffer call</th></tr>
 * <tr><td>take</td><td>{filter?, max?, leaseTtl?}</td><td>{leaseId, transactions[], remaining}</td>
 *     <td>{@code BufferStore.takeWhere(X12Filter.toWhereClause(filter), max, ttl)}; elements via {@code X12ProducerFacade.toElement}</td></tr>
 * <tr><td>ack / release</td><td>{leaseId, elementKeys?}</td><td>{acked|released: n}</td>
 *     <td>{@code BufferStore.ack/release(leaseId, elementKeys)} — partial subsets allowed; 0 = expired/unknown</td></tr>
 * <tr><td>replay</td><td>{filter?}</td><td>{replayed: n}</td><td>{@code BufferStore.replayInFlight(where)}</td></tr>
 * <tr><td>recast</td><td>{filter?, max?}</td><td>{examined, recast, unchanged, failed}</td>
 *     <td>{@code BufferStore.recastable(where, max)} → re-materialize raw → {@code updateMapping}</td></tr>
 * <tr><td>purge</td><td>{olderThan?}</td><td>{purged: n}</td><td>{@code BufferStore.purge(duration)} — acked rows only</td></tr>
 * <tr><td>raw</td><td>{elementKey}</td><td>{elementKey, fileId, gs08, transactionType, raw}</td>
 *     <td>{@code BufferStore.byElementKey}; ST..SE verbatim + ISA/GS context</td></tr>
 * <tr><td>validate</td><td>{elementKey}</td><td>{elementKey, schemaId, stored:{valid,errors[]}, rematerialized:{…}, repsAgree, parserErrors[]}</td>
 *     <td>{@code byElementKey} + schema validation of both reps + imsweb {@code getErrors()}</td></tr>
 * <tr><td>rescan</td><td>{source?}</td><td>{scanned, discovered, consumed, errored}</td>
 *     <td>{@code PollerHandle.rescan(source)} — the only way to force a pickup between intervals</td></tr>
 * </table>
 *
 * <p>Unknown {@code fn} → {@code noSuchObjectError} for {@code /x12-receiver/ops/<fn>}; a
 * malformed {@code filter} → {@code illegalArgumentError}. The foundation ships only
 * {@link #NONE}.
 */
public interface OperationsApi {

    int DEFAULT_MAX = 100;
    int MAX_CAP = 1000;
    java.time.Duration DEFAULT_TTL = java.time.Duration.ofMinutes(5);

    /** Dispatch a {@code /x12-receiver/ops/<fn>} invocation; the map is serialized as the function output. */
    Map<String, Object> invoke(String fn, Map<String, Object> input) throws SQLException;

    /**
     * {@code validateFunctionInput}: the interface {@code ValidationResult}
     * ({@code {valid, errors[{path,message,code}], warnings[{path,message}]}}) for
     * {@code input} against {@code fn}'s declared input — the same check {@link #invoke}
     * applies before it runs anything. Unknown {@code fn} → {@code noSuchObjectError}.
     */
    default Map<String, Object> validateInput(String fn, Object input, boolean strict) {
        throw ProducerException.noSuchObject(ObjectTreeApi.RECEIVER + "/ops/" + fn);
    }

    /** No functions available: every invocation is {@code noSuchObjectError}. */
    OperationsApi NONE = (fn, input) -> {
        throw ProducerException.noSuchObject(ObjectTreeApi.RECEIVER + "/ops/" + fn);
    };
}
