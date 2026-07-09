package com.zerobias.module.hl7.producer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a materialized element against its registered table schema (DESIGN §2.2/§2.3),
 * resolving referenced group / segment / datatype schemas recursively through the
 * {@link SchemaRegistry} (every non-leaf property references a {@code schema:type:} id, so
 * groups, segments, and composite datatypes all recurse the same way).
 *
 * <p>Reports <em>structural</em> conformance problems as a flat list of dot-path messages:
 * a missing required property, a single value where the schema declares a repeating
 * (array) property, a scalar where an object is expected, and undeclared properties. Leaf
 * values — plain scalars and {@code schema:enum:} codes — are accepted without deep
 * type-checking: HL7's primitive/composite ambiguities (an under-delimited composite, an
 * SI rendered as a string) would otherwise raise false positives, and it is the
 * <em>shape</em> (grouping, cardinality, presence) that this op exists to police.
 */
public final class SchemaValidator {

    private static final Gson GSON = new Gson();

    private final SchemaRegistry schemas;

    public SchemaValidator(SchemaRegistry schemas) {
        this.schemas = schemas;
    }

    /** Conformance errors for {@code element} against {@code schemaId}; empty list = valid. */
    public List<String> validate(Map<String, Object> element, String schemaId) {
        List<String> errors = new ArrayList<>();
        validateObject(element, schemaId, "", errors, new LinkedHashSet<>());
        return errors;
    }

    @SuppressWarnings("unchecked")
    private void validateObject(Object value, String schemaId, String path,
            List<String> errors, Set<String> visiting) {
        if (!(value instanceof Map)) {
            errors.add(at(path) + ": expected object (" + schemaId + ")");
            return;
        }
        if (!visiting.add(schemaId)) {
            return;   // cyclic schema graph — stop descending
        }
        try {
            JsonObject schema = schema(schemaId);
            if (schema == null || !schema.has("properties")) {
                return;   // unknown/opaque schema — can't check, don't invent errors
            }
            Map<String, Object> obj = (Map<String, Object>) value;
            Set<String> declared = new LinkedHashSet<>();
            for (JsonElement pe : schema.getAsJsonArray("properties")) {
                JsonObject p = pe.getAsJsonObject();
                String name = p.get("name").getAsString();
                declared.add(name);
                validateProperty(obj, name, p, path, errors, visiting);
            }
            for (String key : obj.keySet()) {
                if (!declared.contains(key)) {
                    errors.add(at(child(path, key)) + ": undeclared property");
                }
            }
        } finally {
            visiting.remove(schemaId);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateProperty(Map<String, Object> obj, String name, JsonObject p,
            String path, List<String> errors, Set<String> visiting) {
        boolean required = p.has("required") && p.get("required").getAsBoolean();
        boolean multi = p.has("multi") && p.get("multi").getAsBoolean();
        String ref = refOf(p);
        String cpath = child(path, name);

        if (!obj.containsKey(name)) {
            if (required) {
                errors.add(at(cpath) + ": missing required property");
            }
            return;
        }
        Object v = obj.get(name);
        if (v == null) {
            return;   // explicit HL7 null — present, accept
        }
        boolean recurse = ref != null && ref.startsWith("schema:type:");
        if (multi) {
            if (!(v instanceof List)) {
                errors.add(at(cpath) + ": expected array (repeating property)");
            } else if (recurse) {
                List<Object> list = (List<Object>) v;
                for (int i = 0; i < list.size(); i++) {
                    validateObject(list.get(i), ref, cpath + "[" + i + "]", errors, visiting);
                }
            }
        } else if (recurse) {
            validateObject(v, ref, cpath, errors, visiting);
        }
    }

    private static String refOf(JsonObject p) {
        if (p.has("references") && p.get("references").isJsonObject()) {
            JsonObject r = p.getAsJsonObject("references");
            if (r.has("schemaId")) {
                return r.get("schemaId").getAsString();
            }
        }
        return null;
    }

    private JsonObject schema(String schemaId) {
        try {
            return GSON.fromJson(schemas.getSchema(schemaId), JsonObject.class);
        } catch (RuntimeException e) {
            return null;   // not served (e.g. an enum stub) — treat as opaque leaf
        }
    }

    private static String child(String path, String name) {
        return path.isEmpty() ? name : path + "." + name;
    }

    private static String at(String path) {
        return path.isEmpty() ? "<root>" : path;
    }
}
