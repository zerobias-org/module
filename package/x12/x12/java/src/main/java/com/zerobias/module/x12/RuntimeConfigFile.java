package com.zerobias.module.x12;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * The module's opaque {@code config} block as found in a runtime-config <em>file</em>
 * — the fallback channels when the {@code MODULE_CONFIG} env is absent (DESIGN §3):
 * <ol>
 *   <li>{@code RUNTIME_CONFIG_FILE} (optional; the node-delivered hydra
 *       {@code DeploymentRuntimeConfig} serialized as JSON) — its {@code config} object.</li>
 *   <li>The {@code runtimeConfig.yml} copied into the image ({@code RUNTIME_CONFIG_YML},
 *       default {@code /opt/module/runtimeConfig.yml}) — its {@code config:} block. This is
 *       the bare-{@code docker run} / e2e fallback; production always gets
 *       {@code MODULE_CONFIG}.</li>
 * </ol>
 * Either file may be JSON or YAML (sniffed on the first non-blank character). Only the
 * {@code config} member is read; there are no listener ports in this module. An
 * absent/unreadable/garbage file yields {@link Optional#empty()} — never a boot crash.
 */
public final class RuntimeConfigFile {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeConfigFile.class);
    private static final Gson GSON = new Gson();

    private final JsonObject config;

    private RuntimeConfigFile(JsonObject config) {
        this.config = config;
    }

    /** The opaque module config block (never null; empty object when the file had none). */
    public JsonObject config() {
        return config;
    }

    /**
     * Load the first available file: {@code RUNTIME_CONFIG_FILE} if set and readable, else
     * the image's {@code runtimeConfig.yml} at {@code ymlPath} if readable, else empty.
     */
    public static Optional<RuntimeConfigFile> load(Map<String, String> env, String ymlPath) {
        final String pointer = env.get("RUNTIME_CONFIG_FILE");
        if (pointer != null && !pointer.isBlank()) {
            Optional<RuntimeConfigFile> f = loadPath(Path.of(pointer.trim()));
            if (f.isPresent()) {
                return f;
            }
        }
        if (ymlPath != null && !ymlPath.isBlank()) {
            return loadPath(Path.of(ymlPath));
        }
        return Optional.empty();
    }

    static Optional<RuntimeConfigFile> loadPath(Path path) {
        if (!Files.isReadable(path)) {
            LOG.info("runtime config file {} not present/readable", path);
            return Optional.empty();
        }
        try {
            RuntimeConfigFile parsed = parse(Files.readString(path));
            LOG.info("Loaded runtime config file {} ({} config key(s))", path, parsed.config.size());
            return Optional.of(parsed);
        } catch (Exception e) {
            LOG.warn("runtime config file {} unreadable/unparseable ({}); ignoring", path, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Parse a JSON or YAML document and extract its {@code config} member. Testable seam;
     * a document without a {@code config} object yields an empty config, and junk throws
     * (callers map that to empty).
     */
    static RuntimeConfigFile parse(String text) {
        final String t = text == null ? "" : text.strip();
        JsonElement root;
        if (t.startsWith("{") || t.startsWith("[")) {
            root = GSON.fromJson(t, JsonElement.class);
        } else {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object loaded = yaml.load(t);
            root = GSON.toJsonTree(loaded);
        }
        if (root == null || !root.isJsonObject()) {
            return new RuntimeConfigFile(new JsonObject());
        }
        JsonElement cfg = root.getAsJsonObject().get("config");
        return new RuntimeConfigFile(cfg != null && cfg.isJsonObject() ? cfg.getAsJsonObject() : new JsonObject());
    }
}
