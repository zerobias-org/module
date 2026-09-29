package com.zerobias.module.x12;

import com.zerobias.module.x12.buffer.BufferStore;
import com.zerobias.module.x12.health.HealthCheck;
import com.zerobias.module.x12.health.PollerStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module's own paths in {@code api.yml} declare what the server returns: the platform error
 * bodies it emits (not an {@code {error,message}} shape nothing sends), no status it cannot
 * produce, and the {@code /healthz} fields {@link HealthCheck} actually writes.
 */
class ApiSpecTest {

    private static final String INTERFACE_ERRORS =
        "./node_modules/@zerobias-org/module-interface-dataproducer/dist/module-interface-dataproducer.yml"
            + "#/components/schemas/";

    @Test
    void connectDeclaresOnlyWhatTheServerReturns() throws IOException {
        Map<String, Object> responses = at(spec(), "paths", "/connect", "post", "responses");
        // there are no credentials, so there is no 401
        assertEquals(Set.of("200", "400", "500"), responses.keySet());
        assertEquals(INTERFACE_ERRORS + "illegalArgumentError", ref(at(responses, "400")));
        assertEquals("#/components/schemas/X12UnexpectedError", ref(at(responses, "500")));
        Map<String, Object> schemas = at(spec(), "components", "schemas");
        assertFalse(schemas.containsKey("ErrorResponse"), "the {error,message} shape is never emitted");
        List<Map<String, Object>> unexpected = cast(at(schemas, "X12UnexpectedError").get("allOf"));
        assertEquals(INTERFACE_ERRORS + "errorModelBase", unexpected.get(0).get("$ref"));
    }

    @Test
    void healthStatusDeclaresWhatTheProbeWrites(@TempDir Path dir) throws Exception {
        Map<String, Object> health = at(spec(), "components", "schemas", "HealthStatus", "properties");
        Map<String, Object> pollerSchema = at(health, "poller");
        Map<String, Object> sourceSchema = at(pollerSchema, "properties", "sources", "items");
        Map<String, Object> dbSchema = at(health, "db");

        try (BufferStore b = new BufferStore(dir.resolve("buffer.db").toString(), false)) {
            Instant t = Instant.parse("2026-09-22T00:00:00Z");
            PollerStatus.SourceStatus failing = new PollerStatus.SourceStatus("inbox", "/in", true, 1, 1, 30,
                t, t, t, t, "io: boom", t.plusSeconds(1));
            PollerStatus poller = new PollerStatus() {
                public boolean up() { return true; }
                public Optional<Instant> lastScan() { return Optional.of(t); }
                public Optional<Instant> lastConsumed() { return Optional.of(t); }
                public boolean backpressure() { return false; }
                public List<SourceStatus> sources() { return List.of(failing); }
            };
            Map<String, Object> status = new HealthCheck(b, poller).status();
            Map<String, Object> emittedPoller = cast(status.get("poller"));
            conforms("poller", pollerSchema, emittedPoller);
            conforms("poller.sources[]", sourceSchema, cast(ApiSpecTest.<List<Object>>cast(emittedPoller.get("sources")).get(0)));
            conforms("db", dbSchema, cast(status.get("db")));
        }
    }

    @Test
    void metadataDeclaresTheUnackedBufferDepth() throws IOException {
        Map<String, Object> schema = at(spec(), "paths", "/metadata", "get", "responses", "200", "content",
            "application/json", "schema");
        assertEquals(List.of("status", "bufferDepth"), schema.get("required"));
        assertTrue(String.valueOf(at(schema, "properties", "bufferDepth").get("description")).contains("Un-acked"));
    }

    @Test
    void theConnectionProfileDeclaresNothingTheDaemonIgnoresBeyondItsDocumentedDefault() throws IOException {
        // senderDiscriminator was read by nothing (like x12Version before it): /by-sender is ISA06
        Map<String, Object> profile = load(Path.of("../connectionProfile.yml"));
        assertEquals(Set.of("ackDurability"), at(profile, "properties").keySet());
        assertFalse(profile.containsKey("required"), "no required field: there is nothing to supply");
    }

    /** Every emitted field is declared, and every required one is emitted. */
    private static void conforms(String where, Map<String, Object> schema, Map<String, Object> emitted) {
        Set<String> declared = at(schema, "properties").keySet();
        for (String key : emitted.keySet()) {
            assertTrue(declared.contains(key), where + " emits undeclared " + key);
        }
        List<String> required = cast(schema.getOrDefault("required", List.of()));
        for (String key : required) {
            assertTrue(emitted.containsKey(key), where + " declares required " + key + " but omits it");
        }
    }

    private static Map<String, Object> spec() throws IOException {
        return load(Path.of("../api.yml"));
    }

    private static Map<String, Object> load(Path file) throws IOException {
        return cast(new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(file)));
    }

    private static String ref(Map<String, Object> response) {
        return (String) at(response, "content", "application/json", "schema").get("$ref");
    }

    private static Map<String, Object> at(Map<String, Object> node, String... path) {
        Map<String, Object> cur = node;
        for (String p : path) {
            cur = cast(cur.get(p));
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }
}
