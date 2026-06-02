package com.zerobias.module.hl7.ext;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericModelClassFactory;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zerobias.module.hl7.buffer.BufferStore;
import com.zerobias.module.hl7.materializer.Materializer;
import com.zerobias.module.hl7.materializer.StructureIndex;
import com.zerobias.module.hl7.materializer.StructureResolver;
import com.zerobias.module.hl7.producer.ObjectTree;
import com.zerobias.module.hl7.producer.SchemaRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 8 validation (DESIGN §7): boot-loading a baked-in extension pack. Stages a
 * sample {@code @zerobias-org/hl7-extension-epic-adt} under a temp {@code EXTENSION_DIR}
 * and asserts the merge surfaces {@code /by-type/ADT_A01_with_ZPV}, routes an EPIC
 * ADT to it (the ZPV segment materializes), and honors activeExtensions /
 * duplicate-id / version-mismatch validation. The base catalog (schemas +
 * structure-index) is loaded from the build-generated classpath resources, exactly
 * as the daemon loads them at runtime.
 */
class ExtensionLoaderIT {

    private static final Gson GSON = new Gson();
    private static final String CR = "\r";

    /** Write a sample epic-adt pack under {@code extRoot/<packName>/extensions/}. */
    private void stageEpicPack(Path extRoot, String packName, String hl7Version) throws Exception {
        Path ext = extRoot.resolve(packName).resolve("extensions");
        Files.createDirectories(ext.resolve("messages"));
        Files.createDirectories(ext.resolve("segments"));

        Files.writeString(ext.resolve("manifest.json"),
            "{\"namespace\":\"epic\",\"hl7Version\":\"" + hl7Version + "\",\"vendor\":\"epic\","
            + "\"discriminators\":[{\"messageCode\":\"ADT\",\"senderEquals\":\"EPIC\","
            + "\"structure\":\"ADT_A01_with_ZPV\"}]}");
        Files.writeString(ext.resolve("messages/ADT_A01_with_ZPV.json"),
            "{\"id\":\"schema:table:hl7v2.epic.ADT_A01_with_ZPV\",\"dataTypes\":[],\"properties\":[]}");
        Files.writeString(ext.resolve("segments/ZPV.json"),
            "{\"id\":\"schema:type:hl7v2.epic.ZPV\",\"dataTypes\":[],\"properties\":[]}");
        Files.writeString(ext.resolve("structure-index.json"),
            "{\"version\":\"v251\",\"messages\":{\"ADT_A01_with_ZPV\":{\"structures\":["
            + "{\"name\":\"msh\",\"structure\":\"MSH\",\"group\":false,\"required\":true,\"multi\":false},"
            + "{\"name\":\"pid\",\"structure\":\"PID\",\"group\":false,\"required\":true,\"multi\":false},"
            + "{\"name\":\"zpv\",\"structure\":\"ZPV\",\"group\":false,\"required\":false,\"multi\":false}"
            + "]}},\"groups\":{},\"segments\":{\"ZPV\":{\"fields\":["
            + "{\"field\":1,\"name\":\"epicVisitId\",\"type\":\"ST\",\"multi\":false,\"required\":false,\"table\":null}"
            + "]}},\"datatypes\":{}}");
    }

    private StructureResolver resolverFor(List<Discriminator> ds) {
        return (code, sender, dflt) -> {
            for (Discriminator d : ds) {
                if (d.matches(code, sender)) {
                    return d.structure();
                }
            }
            return dflt;
        };
    }

    private Message parseGeneric(String er7) throws Exception {
        HapiContext ctx = new DefaultHapiContext();
        ctx.setValidationContext(ValidationContextFactory.noValidation());
        ctx.setModelClassFactory(new GenericModelClassFactory());
        return ctx.getPipeParser().parse(er7);
    }

    @Test
    void loadMergesSurfacesByTypeAndRoutes(@TempDir Path extRoot, @TempDir Path dbDir) throws Exception {
        stageEpicPack(extRoot, "epic-adt", "2.5.1");

        SchemaRegistry registry = SchemaRegistry.fromClasspath();
        StructureIndex index = StructureIndex.fromClasspath("v251");
        int baseSize = registry.size();

        ExtensionLoader.Result result = ExtensionLoader.load(
            extRoot, Set.of(), "2.5.1", registry, index);

        // merged: new schemas served, health descriptor reported
        assertTrue(registry.size() > baseSize, "extension schemas merged");
        assertTrue(registry.getSchema("schema:table:hl7v2.epic.ADT_A01_with_ZPV").contains("epic"));
        assertEquals(1, result.loaded().size());
        assertEquals("epic-adt", result.loaded().get(0).artifact());
        assertEquals(1, result.discriminators().size());

        // by-type surfaces the augmented structure (namespace-agnostic)
        Map<String, String> scopes = new LinkedHashMap<>();
        for (Discriminator d : result.discriminators()) {
            scopes.put(d.structure(), d.whereClause());
        }
        try (BufferStore buffer = new BufferStore(dbDir.resolve("buffer.db").toString(), false)) {
            ObjectTree tree = new ObjectTree(buffer, registry, "v251", scopes);
            List<String> byType = tree.children("/hl7-v2-receiver/by-type").stream()
                .map(o -> o.get("id").toString()).toList();
            assertTrue(byType.contains("/hl7-v2-receiver/by-type/ADT_A01_with_ZPV"),
                "augmented structure listed: " + byType);
            assertTrue(byType.contains("/hl7-v2-receiver/by-type/ADT_A01"), "base structure still listed");

            // the extension collection scopes by the discriminator, not message_structure
            ObjectTree.Collection coll = tree.resolveCollection("/hl7-v2-receiver/by-type/ADT_A01_with_ZPV");
            assertEquals("schema:table:hl7v2.epic.ADT_A01_with_ZPV", coll.schemaId());
            // scoped by BOTH discriminator legs, not just the sender
            assertTrue(coll.scopeWhere().contains("sending_app = 'EPIC'"), coll.scopeWhere());
            assertTrue(coll.scopeWhere().contains("message_code = 'ADT'"), coll.scopeWhere());
        }

        // routing: an EPIC ADT with a ZPV segment materializes the ZPV (via the discriminator)
        Materializer m = new Materializer(index, resolverFor(result.discriminators()));
        String er7 = String.join(CR,
            "MSH|^~\\&|EPIC|HOSP|RECV|DEST|20260101000000||ADT^A01^ADT_A01|MSGX|P|2.5.1",
            "PID|1||5551212^^^EPIC^MR||SMITH^JOHN||19800101|M",
            "ZPV|VISIT-789") + CR;
        JsonObject root = GSON.fromJson(m.toTypedJson(parseGeneric(er7)), JsonObject.class);
        assertTrue(root.has("zpv"), "ZPV materialized: " + root);
        assertEquals("VISIT-789", root.getAsJsonObject("zpv").get("epicVisitId").getAsString());
    }

    @Test
    void activeExtensionsFilterExcludes(@TempDir Path extRoot) throws Exception {
        stageEpicPack(extRoot, "epic-adt", "2.5.1");
        SchemaRegistry registry = SchemaRegistry.fromClasspath();
        StructureIndex index = StructureIndex.fromClasspath("v251");

        // only "other" is active → epic-adt is skipped
        ExtensionLoader.Result result = ExtensionLoader.load(
            extRoot, Set.of("other"), "2.5.1", registry, index);
        assertTrue(result.loaded().isEmpty());
        assertFalse(registry.has("schema:table:hl7v2.epic.ADT_A01_with_ZPV"));
    }

    @Test
    void duplicateSchemaIdAcrossPacksRejected(@TempDir Path extRoot) throws Exception {
        stageEpicPack(extRoot, "epic-adt", "2.5.1");
        stageEpicPack(extRoot, "epic-dup", "2.5.1");   // same epic schema ids
        SchemaRegistry registry = SchemaRegistry.fromClasspath();
        StructureIndex index = StructureIndex.fromClasspath("v251");

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ExtensionLoader.load(extRoot, Set.of(), "2.5.1", registry, index));
        // distinguish from the version-mismatch rejection: this is the dup-id path
        assertTrue(e.getMessage().contains("duplicate schema id"), e.getMessage());
    }

    @Test
    void versionMismatchRejected(@TempDir Path extRoot) throws Exception {
        stageEpicPack(extRoot, "epic-adt", "2.3");      // module is 2.5.1
        SchemaRegistry registry = SchemaRegistry.fromClasspath();
        StructureIndex index = StructureIndex.fromClasspath("v251");

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ExtensionLoader.load(extRoot, Set.of(), "2.5.1", registry, index));
        // distinguish from the dup-id rejection: this is the version-compat path
        assertTrue(e.getMessage().contains("targets HL7"), e.getMessage());
    }
}
