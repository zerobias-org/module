package com.zerobias.module.hl7.listener;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import com.zerobias.module.hl7.buffer.BufferRow;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.buffer.Lease;
import com.zerobias.module.hl7.materializer.EnvelopeMaterializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end receive path (DESIGN §4): a real HAPI MLLP client sends an ADT^A01
 * to the listener; we assert the AA ack, that the row was buffered with the
 * right envelope + materialized JSON, and that a duplicate re-send is deduped
 * while still getting AA.
 */
class Hl7ListenerIT {

    private static final String ER7 = String.join("\r",
        "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260529103000||ADT^A01^ADT_A01|MSG00001|P|2.5.1",
        "EVN|A01|20260529103000",
        "PID|1||5551212^^^EPIC^MR||SMITH^JOHN||19800101|M",
        "PV1|1|I") + "\r";

    @Test
    void receivesBuffersAndAcks(@TempDir Path dir) throws Exception {
        final int port = freePort();
        try (BufferStore buffer = new BufferStore(dir.resolve("buffer.db").toString(), false);
             Hl7ListenerService listener = new Hl7ListenerService(
                 port, new BufferingApp(buffer, new EnvelopeMaterializer(), "v251"));
             HapiContext client = new DefaultHapiContext()) {

            listener.start();
            final Connection conn = client.newClient("localhost", port, false);

            // 1. Send the message → expect AA, one buffered row.
            final Message resp = conn.getInitiator().sendAndReceive(client.getPipeParser().parse(ER7));
            assertEquals("AA", new Terser(resp).get("/MSA-1"), "accepted");
            assertEquals("MSG00001", new Terser(resp).get("/MSA-2"), "echoes control id");
            assertEquals(1, buffer.count());

            // 2. The buffered row carries the parsed envelope + materialized JSON.
            final Lease lease = buffer.take(null, 10, Duration.ofMinutes(5));
            final BufferRow row = lease.messages().get(0);
            assertEquals("MSG00001", row.controlId());
            assertEquals("ADT_A01", row.messageStructure());
            assertEquals("ADT", row.messageCode());
            assertEquals("A01", row.triggerEvent());
            assertEquals("EPIC", row.sendingApp());
            assertEquals("schema:table:hl7v2.v251.ADT_A01", row.schemaId());
            assertNotNull(row.rawEr7());
            assertTrue(row.mappedJson().contains("\"patientFamilyName\":\"SMITH\""),
                "materialized JSON: " + row.mappedJson());
            assertTrue(row.mappedJson().contains("\"dateOfBirth\":\"1980-01-01\""),
                "normalized DOB: " + row.mappedJson());
            buffer.ack(lease.leaseId(), null);

            // 3. Duplicate re-send → still AA, but deduped (count unchanged).
            final Message resp2 = conn.getInitiator().sendAndReceive(client.getPipeParser().parse(ER7));
            assertEquals("AA", new Terser(resp2).get("/MSA-1"));
            assertEquals(1, buffer.count(), "duplicate controlId dropped");

            conn.close();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
