package com.zerobias.module.hl7.ext;

import com.zerobias.module.hl7.health.HealthCheck;
import com.zerobias.module.hl7.materializer.StructureIndex;
import com.zerobias.module.hl7.producer.SchemaRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads baked-in extension packs at boot (DESIGN §7.3). Extensions are npm deps
 * baked into the image under {@code EXTENSION_DIR}; nothing is fetched or mounted
 * at runtime. For each pack ({@code <EXTENSION_DIR>/<pack>/extensions/}) this:
 *
 * <ol>
 *   <li>reads {@code manifest.json};</li>
 *   <li>skips packs not in {@code activeExtensions} (when that list is non-empty);</li>
 *   <li>validates HL7 version compatibility (module-internal — there is NO platform
 *       namespace-ownership check, §7.1);</li>
 *   <li>merges the pack's schemas into the {@link SchemaRegistry} (which rejects
 *       duplicate ids) and its {@code structure-index.json} into the materializer
 *       {@link StructureIndex};</li>
 *   <li>collects the pack's {@link Discriminator}s and a health descriptor.</li>
 * </ol>
 *
 * Returns the discriminators + per-pack health info; the registry and index are
 * mutated in place. The {@code /by-type/<structure>} objects surface from the
 * merged registry (the object tree reads it).
 */
public final class ExtensionLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ExtensionLoader.class);

    /** Outcome of a load: routing rules + health descriptors for the active packs. */
    public record Result(List<Discriminator> discriminators, List<HealthCheck.ExtensionInfo> loaded) {
        public static Result empty() {
            return new Result(List.of(), List.of());
        }
    }

    private ExtensionLoader() {
    }

    /**
     * @param extensionDir     in-image {@code EXTENSION_DIR}
     * @param activeExtensions pack names to activate (from {@code MODULE_CONFIG}); empty = all
     * @param hl7Version       the module's HL7 version (e.g. {@code 2.7}) for compat check
     */
    public static Result load(Path extensionDir, Set<String> activeExtensions, String hl7Version,
            SchemaRegistry registry, StructureIndex index) {
        if (extensionDir == null || !Files.isDirectory(extensionDir)) {
            return Result.empty();
        }
        List<Path> packs;
        try (Stream<Path> dirs = Files.list(extensionDir)) {
            packs = dirs.filter(Files::isDirectory).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list extension dir " + extensionDir, e);
        }

        List<Discriminator> discriminators = new ArrayList<>();
        List<HealthCheck.ExtensionInfo> loaded = new ArrayList<>();

        for (Path pack : packs) {
            String packName = pack.getFileName().toString();
            if (!activeExtensions.isEmpty() && !activeExtensions.contains(packName)) {
                LOG.info("extension {} not in activeExtensions — skipping", packName);
                continue;
            }
            Path extDir = pack.resolve("extensions");
            Path manifestFile = extDir.resolve("manifest.json");
            if (!Files.isRegularFile(manifestFile)) {
                LOG.warn("extension {} has no extensions/manifest.json — skipping", packName);
                continue;
            }

            ExtensionManifest manifest = ExtensionManifest.fromFile(manifestFile);
            if (manifest.hl7Version != null && !manifest.hl7Version.equals(hl7Version)) {
                throw new IllegalStateException("extension " + packName + " targets HL7 "
                    + manifest.hl7Version + " but module is " + hl7Version);
            }

            int before = registry.size();
            registry.mergeExtension(extDir);                       // throws on duplicate id
            int schemasLoaded = registry.size() - before;

            Path extIndex = extDir.resolve("structure-index.json");
            if (Files.isRegularFile(extIndex)) {
                index.merge(StructureIndex.fromFile(extIndex));
            }

            discriminators.addAll(manifest.discriminators());
            loaded.add(new HealthCheck.ExtensionInfo(packName, schemasLoaded));
            LOG.info("loaded extension {} (namespace={}, {} schemas, {} discriminators)",
                packName, manifest.namespace, schemasLoaded, manifest.discriminators().size());
        }
        return new Result(discriminators, loaded);
    }
}
