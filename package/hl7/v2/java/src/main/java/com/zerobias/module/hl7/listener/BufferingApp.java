package com.zerobias.module.hl7.listener;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.protocol.ReceivingApplication;
import ca.uhn.hl7v2.protocol.ReceivingApplicationException;
import ca.uhn.hl7v2.util.Terser;
import com.zerobias.module.hl7.buffer.BufferRow;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.buffer.MessageStatus;
import com.zerobias.module.hl7.materializer.MessageMaterializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * The HAPI receiving application (DESIGN §4.2). For each inbound message it
 * extracts the envelope (via {@link Terser}), materializes typed JSON, persists
 * a {@link BufferRow}, and ACKs:
 *
 * <ul>
 *   <li><b>Ack-on-persist:</b> {@code MSA|AA} is returned only after the SQLite
 *       insert commits — an AA on the wire means the row survives a restart.</li>
 *   <li><b>Dedup:</b> a duplicate {@code controlId} is silently dropped by the
 *       buffer; the sender still gets AA (HL7 re-sends are routine).</li>
 *   <li><b>Failure:</b> any error persisting yields {@code MSA|AE} so the sender
 *       retries (DESIGN §2.7).</li>
 * </ul>
 */
public final class BufferingApp implements ReceivingApplication<Message> {

    private static final Logger LOG = LoggerFactory.getLogger(BufferingApp.class);

    private final BufferStore buffer;
    private final MessageMaterializer materializer;
    private final String versionSlot;
    private final String sourcePort;
    private final Clock clock;

    public BufferingApp(BufferStore buffer, MessageMaterializer materializer, String versionSlot) {
        this(buffer, materializer, versionSlot, null, Clock.systemUTC());
    }

    /** One {@code BufferingApp} per MLLP listener; {@code sourcePort} is that listener's name. */
    public BufferingApp(BufferStore buffer, MessageMaterializer materializer, String versionSlot,
            String sourcePort) {
        this(buffer, materializer, versionSlot, sourcePort, Clock.systemUTC());
    }

    public BufferingApp(BufferStore buffer, MessageMaterializer materializer, String versionSlot,
            String sourcePort, Clock clock) {
        this.buffer = buffer;
        this.materializer = materializer;
        this.versionSlot = versionSlot;
        this.sourcePort = sourcePort;
        this.clock = clock;
    }

    @Override
    public boolean canProcess(Message message) {
        return true;
    }

    @Override
    public Message processMessage(Message in, Map<String, Object> metadata)
            throws ReceivingApplicationException, HL7Exception {
        try {
            final Terser t = new Terser(in);
            final String code = nz(t.get("/MSH-9-1"));
            final String trigger = nz(t.get("/MSH-9-2"));
            String structure = t.get("/MSH-9-3");
            if (structure == null || structure.isEmpty()) {
                structure = code + "_" + trigger; // e.g. ADT_A01 when MSH-9-3 omitted
            }

            final BufferRow row = new BufferRow(
                0,
                Instant.now(clock),
                t.get("/MSH-10"),
                structure,
                code,
                trigger,
                t.get("/MSH-3"),
                t.get("/MSH-4"),
                t.get("/MSH-12"),
                "schema:table:hl7v2." + versionSlot + "." + structure,
                in.encode().getBytes(StandardCharsets.UTF_8),
                materializer.toTypedJson(in),
                MessageStatus.NEW,
                null, null, null,
                sourcePort);

            // Commit BEFORE acking. ON CONFLICT(control_id) DO NOTHING makes
            // re-sends a no-op that still gets AA.
            buffer.insert(row);
            return AckBuilder.accept(in);
        } catch (Exception e) {
            LOG.warn("rejecting message (AE): {}", e.getMessage());
            try {
                return AckBuilder.error(in, e.getMessage());
            } catch (Exception ackFailure) {
                throw new ReceivingApplicationException(ackFailure);
            }
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
