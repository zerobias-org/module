package com.zerobias.module.hl7.listener;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericModelClassFactory;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.materializer.EnvelopeMaterializer;
import com.zerobias.module.hl7.materializer.MessageMaterializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The acknowledgement contract (DESIGN §4.2 / §2.7): AA only on durable persist,
 * AE on any failure so the sender retries. The happy path is covered by
 * Hl7ListenerIT over real MLLP; this covers the AE failure path (untested via the
 * listener) and AckBuilder directly.
 */
class BufferingAppTest {

    private static final String ADT =
        "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260601120000||ADT^A01^ADT_A01|MSG1|P|2.7\r"
        + "PID|1||5551212^^^EPIC^MR||DOE^JANE||19850315|F\r";

    private Message parse(String er7) throws Exception {
        HapiContext ctx = new DefaultHapiContext();
        ctx.setValidationContext(ValidationContextFactory.noValidation());
        ctx.setModelClassFactory(new GenericModelClassFactory());
        return ctx.getPipeParser().parse(er7);
    }

    @Test
    void ackBuilderProducesAaAndAe() throws Exception {
        Message in = parse(ADT);
        assertEquals("AA", new Terser(AckBuilder.accept(in)).get("/MSA-1"));
        assertEquals("AE", new Terser(AckBuilder.error(in, "boom")).get("/MSA-1"));
    }

    @Test
    void failureToMaterializeYieldsAeAndDoesNotPersist(@TempDir Path dir) throws Exception {
        try (BufferStore buffer = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            // Materializer that always throws — processMessage must catch → MSA|AE,
            // and nothing must be written to the buffer.
            MessageMaterializer boom = msg -> {
                throw new RuntimeException("materialize failed");
            };
            BufferingApp app = new BufferingApp(buffer, boom, "v27");

            Message ack = app.processMessage(parse(ADT), new java.util.HashMap<>());

            assertEquals("AE", new Terser(ack).get("/MSA-1"), "failure must NAK with AE");
            assertEquals(0, buffer.count(), "a failed message must not be persisted");
        }
    }

    @Test
    void successPersistsAndAcksAa(@TempDir Path dir) throws Exception {
        try (BufferStore buffer = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            BufferingApp app = new BufferingApp(buffer, new EnvelopeMaterializer(), "v27");
            Message ack = app.processMessage(parse(ADT), new java.util.HashMap<>());
            assertEquals("AA", new Terser(ack).get("/MSA-1"));
            assertEquals(1, buffer.count());
            assertEquals("MSG1", new Terser(ack).get("/MSA-2"), "ACK echoes the control id");
        }
    }
}
