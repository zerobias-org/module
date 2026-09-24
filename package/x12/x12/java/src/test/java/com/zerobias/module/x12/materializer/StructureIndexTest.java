package com.zerobias.module.x12.materializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.zerobias.module.x12.parser.TransactionTypes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Loads the codegen output from the classpath (structure-index/<GS08>.json). */
class StructureIndexTest {

    @Test
    void loads835IndexFromClasspath() {
        StructureIndex idx = StructureIndex.fromClasspath("005010X221A1").orElseThrow();
        assertEquals("005010X221A1", idx.gs08);
        assertEquals("835", idx.transactionType);
        assertEquals("schema:table:x12.005010X221A1.835", idx.tableSchemaId);
        assertEquals("ST_LOOP", idx.transactionLoop);
        StructureIndex.LoopEntry st = idx.transactionEntry();
        assertNotNull(st);
        assertEquals("st", st.structures.get(0).name);
        assertEquals("header", st.structures.get(1).name);
        assertTrue(st.structures.get(2).isLoop());
        assertEquals("detail", st.structures.get(2).name);
        StructureIndex.SegmentEntry clp = idx.segment("CLP");
        assertEquals("clp04", clp.fields.get(3).name);
        assertEquals("R", clp.fields.get(3).x12Type);
        assertEquals("C003", idx.segment("SVC").fields.get(0).composite);
        assertEquals("c00302", idx.composite("C003").fields.get(1).name);
    }

    @Test
    void aliasesResolveThroughTransactionTypes() {
        assertEquals("005010X222A1", StructureIndex.fromClasspath("005010X222").orElseThrow().gs08);
        assertEquals("005010X223A2", StructureIndex.fromClasspath("005010x223a1").orElseThrow().gs08);
        assertEquals(Optional.empty(), StructureIndex.fromClasspath("005010X999"));
        assertEquals(Optional.empty(), StructureIndex.fromClasspath(null));
    }

    @Test
    void resolverCachesOneIndexPerCanonicalGuide() {
        StructureResolver r = new StructureResolver();
        assertEquals("schema:table:x12.005010X222A1.837P", r.resolve("005010X222").orElseThrow().tableSchemaId);
        assertEquals(Optional.empty(), r.resolve("005010X999"));
        assertSame(r.resolve("005010X221A1").orElseThrow(), r.resolve("005010X221").orElseThrow());
    }

    @Test
    void loopPropertiesAreTheNamesTheIndexReferencesThemBy() {
        StructureIndex idx = StructureIndex.fromClasspath("005010X222A1").orElseThrow();
        assertEquals("loop2300", idx.loopProperty("2300"));
        assertEquals("loop2010AA", idx.loopProperty("2010AA"));
        assertEquals("header", idx.loopProperty("HEADER"));
        assertEquals("gsLoop", idx.loopProperty("GS_LOOP"));
        assertEquals("NOT_A_LOOP", idx.loopProperty("NOT_A_LOOP"), "unknown xid comes back verbatim");
    }

    /**
     * This class is a hand-kept copy of the codegen's model. Every key the codegen writes must be
     * a field here of a compatible type, and every field here must be written by the codegen for
     * some guide — either direction of drift fails.
     */
    @Test
    void generatedIndexesHaveExactlyThisClassShape() throws IOException {
        Set<String> seen = new HashSet<>();
        for (TransactionTypes.Guide g : TransactionTypes.guides()) {
            String resource = StructureIndex.RESOURCE_DIR + g.canonicalGs08() + ".json";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(in, resource + " not generated for a guide in the table");
                JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                assertShape(json, StructureIndex.class, g.canonicalGs08(), seen);
            }
        }
        for (Class<?> c : List.of(StructureIndex.class, StructureIndex.LoopEntry.class, StructureIndex.StructureRef.class,
                StructureIndex.SegmentEntry.class, StructureIndex.CompositeEntry.class, StructureIndex.FieldEntry.class)) {
            for (Field f : c.getFields()) {
                if (isData(f)) {
                    assertTrue(seen.contains(c.getSimpleName() + "." + f.getName()),
                        c.getSimpleName() + "." + f.getName() + " is never written by the codegen");
                }
            }
        }
    }

    private static void assertShape(JsonElement json, Type type, String path, Set<String> seen) {
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            Type item = pt.getActualTypeArguments()[pt.getActualTypeArguments().length - 1];
            if (List.class.isAssignableFrom(raw)) {
                assertTrue(json.isJsonArray(), path + " should be an array");
                int i = 0;
                for (JsonElement e : json.getAsJsonArray()) {
                    assertShape(e, item, path + "[" + i++ + "]", seen);
                }
            } else if (Map.class.isAssignableFrom(raw)) {
                assertTrue(json.isJsonObject(), path + " should be an object");
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                    assertShape(e.getValue(), item, path + "." + e.getKey(), seen);
                }
            } else {
                fail(path + ": unexpected field type " + type);
            }
            return;
        }
        Class<?> c = (Class<?>) type;
        if (c == String.class) {
            assertTrue(json.isJsonPrimitive() && json.getAsJsonPrimitive().isString(), path + " should be a string");
        } else if (c == boolean.class || c == Boolean.class) {
            assertTrue(json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean(), path + " should be a boolean");
        } else if (c == int.class || c == Integer.class) {
            assertTrue(json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber(), path + " should be a number");
        } else {
            assertTrue(json.isJsonObject(), path + " should be an object");
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                Field f;
                try {
                    f = c.getField(e.getKey());
                } catch (NoSuchFieldException x) {
                    throw new AssertionError(path + "." + e.getKey() + ": the codegen writes a field "
                        + c.getSimpleName() + " does not have");
                }
                assertTrue(isData(f), path + "." + e.getKey() + " maps to a static or transient field");
                seen.add(c.getSimpleName() + "." + f.getName());
                if (!e.getValue().isJsonNull()) {
                    assertShape(e.getValue(), f.getGenericType(), path + "." + e.getKey(), seen);
                }
            }
        }
    }

    private static boolean isData(Field f) {
        return !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers());
    }
}
