package com.zerobias.module.hl7.producer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for {@link SchemaValidator} over the table-schema dialect, using a small
 * hand-written schema set (a message referencing a segment/group and a repeating group)
 * served through a real {@link SchemaRegistry}. Exercises the structural rules the
 * {@code ops/validate} op relies on: required-present, undeclared-key, cardinality
 * (multi ⇒ array), recursion into {@code schema:type:} references, and {@code schema:enum:}
 * / scalar values treated as accepted leaves.
 */
class SchemaValidatorTest {

    private static final String ROOT = "schema:table:hl7v2.vT.ROOT";
    private SchemaValidator validator;

    @BeforeEach
    void setUp(@TempDir Path dir) throws Exception {
        // ROOT = { controlId(req), status(req, enum-ref leaf), patient(req, ->PATIENT),
        //          orders(multi, ->ORDER) }
        write(dir, "schemas/vT/messages/ROOT.json", ROOT,
            "{\"name\":\"controlId\",\"required\":true},"
          + "{\"name\":\"status\",\"required\":true,\"references\":{\"schemaId\":\"schema:enum:hl7v2.vT.ST\"}},"
          + "{\"name\":\"patient\",\"required\":true,\"references\":{\"schemaId\":\"schema:type:hl7v2.vT.PATIENT\"}},"
          + "{\"name\":\"orders\",\"multi\":true,\"references\":{\"schemaId\":\"schema:type:hl7v2.vT.ORDER\"}}");
        write(dir, "schemas/vT/segments/PATIENT.json", "schema:type:hl7v2.vT.PATIENT",
            "{\"name\":\"familyName\",\"required\":true}");
        write(dir, "schemas/vT/groups/ORDER.json", "schema:type:hl7v2.vT.ORDER",
            "{\"name\":\"orderId\",\"required\":true}");
        // note: schema:enum:hl7v2.vT.ST is deliberately NOT served — enum refs are leaves.
        validator = new SchemaValidator(SchemaRegistry.fromDirectory(dir));
    }

    private static void write(Path dir, String rel, String id, String props) throws Exception {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, "{\"id\":\"" + id + "\",\"dataTypes\":[],\"properties\":[" + props + "]}\n");
    }

    private Map<String, Object> validElement() {
        Map<String, Object> el = new LinkedHashMap<>();
        el.put("controlId", "C1");
        el.put("status", "F");                              // enum-ref leaf: plain scalar accepted
        el.put("patient", Map.of("familyName", "DOE"));     // recurse ->PATIENT
        el.put("orders", List.of(Map.of("orderId", "O1"), Map.of("orderId", "O2")));
        return el;
    }

    @Test
    void conformingElementHasNoErrors() {
        assertTrue(validator.validate(validElement(), ROOT).isEmpty());
    }

    @Test
    void missingRequiredIsReported() {
        Map<String, Object> el = validElement();
        el.remove("patient");
        List<String> errors = validator.validate(el, ROOT);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("patient") && errors.get(0).contains("missing required"));
    }

    @Test
    void undeclaredPropertyIsReported() {
        Map<String, Object> el = validElement();
        el.put("bogus", "x");
        assertTrue(validator.validate(el, ROOT).stream().anyMatch(e -> e.contains("bogus") && e.contains("undeclared")));
    }

    @Test
    void repeatingPropertyMustBeArray() {
        Map<String, Object> el = validElement();
        el.put("orders", Map.of("orderId", "O1"));   // object where the schema declares multi
        assertTrue(validator.validate(el, ROOT).stream().anyMatch(e -> e.contains("orders") && e.contains("array")));
    }

    @Test
    void recursesIntoReferencedSchema() {
        Map<String, Object> el = validElement();
        el.put("patient", Map.of());   // familyName (required in PATIENT) missing
        assertTrue(validator.validate(el, ROOT).stream()
            .anyMatch(e -> e.contains("patient.familyName") && e.contains("missing required")));
    }

    @Test
    void arrayElementErrorsCarryIndexedPath() {
        Map<String, Object> el = validElement();
        el.put("orders", List.of(Map.of("orderId", "O1"), Map.of()));   // second order missing orderId
        assertTrue(validator.validate(el, ROOT).stream()
            .anyMatch(e -> e.contains("orders[1].orderId") && e.contains("missing required")));
    }

    @Test
    void explicitNullSatisfiesPresenceWithoutRecursing() {
        Map<String, Object> el = validElement();
        Map<String, Object> withNull = new LinkedHashMap<>(el);
        withNull.put("patient", null);   // present-but-null: accepted, no recursion attempted
        assertFalse(validator.validate(withNull, ROOT).stream().anyMatch(e -> e.contains("patient")));
    }
}
