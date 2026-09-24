package com.zerobias.module.x12;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.zerobias.module.x12.buffer.RetentionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The opaque, module-defined runtime config the platform transports verbatim as the
 * {@code MODULE_CONFIG} env (DESIGN §3, {@code runtimeConfig.yml} {@code config:}).
 * The platform never parses it — this module owns its shape:
 *
 * <pre>
 * { "sources": [ { "name": "inbox", "path": "/var/lib/x12/inbox",
 *                  "pattern": "*.{x12,edi,txt,835,837,277,999,dat}",
 *                  "pollIntervalSec": 30, "stableForSec": 60 } ],
 *   "consumedSuffix": ".done", "errorSuffix": ".error",
 *   "ackDurability": "full",
 *   "maxFileBytes": 67108864,
 *   "retention": { "maxBytes": 10737418240, "maxAge": "P90D" },
 *   "allowBareTransactionSets": false,
 *   "allowFileManagement": false }
 * </pre>
 *
 * <p>{@code allowFileManagement} opens the DataProducer write surface over the mounted
 * volume — {@code uploadBinaryContent}, {@code createChildObject} (mkdir) and
 * {@code deleteObject} under {@code /x12-receiver/inbox} (DESIGN §2.9). It defaults to
 * <b>false</b>: a production receiver takes files from the feed, and an open upload path
 * would let any Hub-authenticated caller inject claims. Enable it per deployment (e2e,
 * operator-driven replay) and {@code isSupported} answers accordingly.
 *
 * <p>Resolution order ({@link #resolve}): {@code MODULE_CONFIG} env → the {@code config}
 * block of a runtime-config file ({@link RuntimeConfigFile}: node JSON, then the image's
 * {@code runtimeConfig.yml}) → {@link #defaults()}. Defaults apply only when no config is
 * present at all; a config that is present but malformed — bad JSON, a wrong-typed or
 * out-of-range value, an unknown key, an unusable {@code sources[]} entry, two sources on
 * the same or nested directories — throws {@link InvalidConfigException} and the daemon
 * exits at boot. Silently running on defaults would watch the wrong directory, keep the
 * wrong retention or drop the durability an operator asked for, with nothing to show for
 * it but an unexpected quiet. Directory validation is separate ({@link #validateSources()})
 * because it touches the filesystem.
 */
public record ModuleRuntimeConfig(
        List<SourceConfig> sources,
        String consumedSuffix,
        String errorSuffix,
        boolean fullDurability,
        RetentionConfig retention,
        boolean allowBareTransactionSets,
        long maxFileBytes,
        boolean allowFileManagement) {

    private static final Logger LOG = LoggerFactory.getLogger(ModuleRuntimeConfig.class);
    private static final Gson GSON = new Gson();

    public static final String DEFAULT_CONSUMED_SUFFIX = ".done";
    public static final String DEFAULT_ERROR_SUFFIX = ".error";
    public static final String DEFAULT_SOURCE_NAME = "inbox";
    public static final String DEFAULT_SOURCE_PATH = "/var/lib/x12/inbox";
    public static final String DEFAULT_SOURCE_PATTERN = "*.{x12,edi,txt,835,837,277,999,dat}";
    /** 64 MiB: well above real-world 835/837 drops, well below what the parser can hold in the default heap. */
    public static final long DEFAULT_MAX_FILE_BYTES = 64L * 1024 * 1024;
    /**
     * 128 MiB. A file may be one transaction set, stored whole as one {@code raw_x12} value and
     * one {@code mapped_json} value, and the JSON runs about 3–4½× the X12 (the envelope and a
     * property name per element). SQLite refuses any single value over 1,000,000,000 bytes, so
     * past ~210 MiB such a file could never be stored; 128 MiB keeps a margin under that
     * (the consumer still sends a refused file to {@code .error}), and the whole file must fit
     * in the heap several times over anyway.
     */
    public static final long MAX_MAX_FILE_BYTES = 128L * 1024 * 1024;

    private static final Set<String> KEYS = Set.of("sources", "consumedSuffix", "errorSuffix", "ackDurability",
        "maxFileBytes", "retention", "allowBareTransactionSets", "allowFileManagement");
    private static final Set<String> SOURCE_KEYS = Set.of("name", "path", "pattern", "pollIntervalSec", "stableForSec");
    private static final Set<String> RETENTION_KEYS = Set.of("maxBytes", "maxAge");

    /** A present-but-unusable module config; fatal at boot. */
    public static final class InvalidConfigException extends IllegalArgumentException {
        public InvalidConfigException(String message) {
            super("invalid module config: " + message);
        }
    }

    public ModuleRuntimeConfig {
        sources = List.copyOf(sources);
        if (maxFileBytes <= 0 || maxFileBytes > MAX_MAX_FILE_BYTES) {
            throw new InvalidConfigException("maxFileBytes must be between 1 and " + MAX_MAX_FILE_BYTES
                + ", got " + maxFileBytes);
        }
    }

    /** A receive-only config: {@code allowFileManagement} off, as every deployment starts. */
    public ModuleRuntimeConfig(List<SourceConfig> sources, String consumedSuffix, String errorSuffix,
            boolean fullDurability, RetentionConfig retention, boolean allowBareTransactionSets, long maxFileBytes) {
        this(sources, consumedSuffix, errorSuffix, fullDurability, retention, allowBareTransactionSets, maxFileBytes,
            false);
    }

    /** The image defaults (mirror {@code runtimeConfig.yml} minus retention, which is unbounded). */
    public static ModuleRuntimeConfig defaults() {
        return new ModuleRuntimeConfig(
            List.of(new SourceConfig(DEFAULT_SOURCE_NAME, DEFAULT_SOURCE_PATH, DEFAULT_SOURCE_PATTERN,
                SourceConfig.DEFAULT_POLL_INTERVAL_SEC, SourceConfig.DEFAULT_STABLE_FOR_SEC)),
            DEFAULT_CONSUMED_SUFFIX, DEFAULT_ERROR_SUFFIX, true, RetentionConfig.none(), false,
            DEFAULT_MAX_FILE_BYTES, false);
    }

    /** Resolve from the process env and the image's runtimeConfig.yml location. */
    public static ModuleRuntimeConfig fromEnv(ModuleConfig module) {
        return resolve(System.getenv(), module.runtimeConfigYml());
    }

    /** Testable seam: env map + yml path, no process-env access. */
    public static ModuleRuntimeConfig resolve(Map<String, String> env, String ymlPath) {
        final String json = env.get("MODULE_CONFIG");
        if (json != null && !json.isBlank()) {
            LOG.info("Module config source: MODULE_CONFIG env");
            return parse(json);
        }
        Optional<RuntimeConfigFile> file = RuntimeConfigFile.load(env, ymlPath);
        if (file.isPresent()) {
            LOG.info("Module config source: runtime config file (MODULE_CONFIG absent)");
            return fromConfigObject(file.get().config());
        }
        LOG.info("Module config source: built-in defaults (no MODULE_CONFIG, no runtime config file)");
        return defaults();
    }

    /** Parse the {@code MODULE_CONFIG} JSON text; null/blank = absent = defaults. */
    public static ModuleRuntimeConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        final JsonElement el;
        try {
            el = GSON.fromJson(json, JsonElement.class);
        } catch (JsonParseException malformed) {
            throw new InvalidConfigException("MODULE_CONFIG is not valid JSON (" + malformed.getMessage() + ")");
        }
        if (el == null || !el.isJsonObject()) {
            throw new InvalidConfigException("MODULE_CONFIG must be a JSON object");
        }
        return fromConfigObject(el.getAsJsonObject());
    }

    /** Build from an already-parsed {@code config} object (env JSON or file block); every key is optional. */
    public static ModuleRuntimeConfig fromConfigObject(JsonObject obj) {
        if (obj == null) {
            return defaults();
        }
        rejectUnknownKeys(obj, KEYS, "");
        ModuleRuntimeConfig d = defaults();
        List<SourceConfig> sources = obj.has("sources") ? parseSources(obj.get("sources")) : d.sources();
        String consumed = suffix(obj, "consumedSuffix", d.consumedSuffix());
        String error = suffix(obj, "errorSuffix", d.errorSuffix());
        boolean full = durability(obj);
        long maxFileBytes = obj.has("maxFileBytes")
            ? positiveLong(obj.get("maxFileBytes"), "maxFileBytes") : d.maxFileBytes();
        RetentionConfig retention = obj.has("retention") ? parseRetention(obj.get("retention")) : RetentionConfig.none();
        boolean bare = obj.has("allowBareTransactionSets")
            && bool(obj.get("allowBareTransactionSets"), "allowBareTransactionSets");
        boolean fileManagement = obj.has("allowFileManagement")
            && bool(obj.get("allowFileManagement"), "allowFileManagement");
        return new ModuleRuntimeConfig(sources, consumed, error, full, retention, bare, maxFileBytes, fileManagement);
    }

    /**
     * Boot validation (DESIGN §3): every source path must exist, be a directory and take
     * renames to both configured suffixes; names must be distinct and no two sources may
     * resolve to the same or nested directories (symlinks resolved); suffixes must be
     * non-blank and distinct.
     * Returns the list of problems (empty = OK). The caller logs and exits 1 on any.
     */
    public List<String> validateSources() {
        List<String> problems = new ArrayList<>();
        if (sources.isEmpty()) {
            problems.add("no sources configured");
        }
        Set<String> names = new HashSet<>();
        Map<Path, String> realDirs = new HashMap<>();
        for (SourceConfig s : sources) {
            if (!names.add(s.name())) {
                problems.add("duplicate source name: " + s.name());
            }
            String p = s.validate(consumedSuffix, errorSuffix);
            if (p != null) {
                problems.add(p);
                continue;
            }
            try {
                Path real = s.dir().toRealPath();
                for (Map.Entry<Path, String> other : realDirs.entrySet()) {
                    if (real.startsWith(other.getKey()) || other.getKey().startsWith(real)) {
                        problems.add("sources '" + other.getValue() + "' and '" + s.name()
                            + "' resolve to the same or nested directories: " + other.getKey() + ", " + real);
                    }
                }
                realDirs.put(real, s.name());
            } catch (IOException e) {
                problems.add("source '" + s.name() + "': cannot resolve " + s.path() + " (" + e + ")");
            }
        }
        if (consumedSuffix == null || consumedSuffix.isBlank() || errorSuffix == null || errorSuffix.isBlank()) {
            problems.add("consumedSuffix and errorSuffix must be non-blank");
        } else if (consumedSuffix.equals(errorSuffix)) {
            problems.add("consumedSuffix and errorSuffix must differ");
        }
        return problems;
    }

    // --- parsing helpers -------------------------------------------------------

    private static List<SourceConfig> parseSources(JsonElement el) {
        if (!el.isJsonArray() || el.getAsJsonArray().isEmpty()) {
            throw new InvalidConfigException("sources must be a non-empty array, got " + el);
        }
        List<SourceConfig> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<Path> dirs = new ArrayList<>();
        int i = 0;
        for (JsonElement entry : el.getAsJsonArray()) {
            String at = "sources[" + i++ + "]";
            if (!entry.isJsonObject()) {
                throw new InvalidConfigException(at + " must be an object, got " + entry);
            }
            JsonObject s = entry.getAsJsonObject();
            rejectUnknownKeys(s, SOURCE_KEYS, at + ".");
            String name = requiredString(s, "name", at);
            String path = requiredString(s, "path", at);
            String pattern = s.has("pattern") ? string(s.get("pattern"), at + ".pattern") : null;
            int poll = s.has("pollIntervalSec")
                ? integer(s.get("pollIntervalSec"), at + ".pollIntervalSec") : SourceConfig.DEFAULT_POLL_INTERVAL_SEC;
            int stable = s.has("stableForSec")
                ? integer(s.get("stableForSec"), at + ".stableForSec") : SourceConfig.DEFAULT_STABLE_FOR_SEC;
            SourceConfig source;
            try {
                source = new SourceConfig(name, path, pattern, poll, stable);
            } catch (IllegalArgumentException bad) {
                throw new InvalidConfigException(at + ": " + bad.getMessage());
            }
            if (!names.add(name)) {
                throw new InvalidConfigException(at + ": duplicate source name '" + name + "'");
            }
            // Two pollers on one directory would race each other for every file.
            Path dir = source.normalizedDir();
            for (int j = 0; j < dirs.size(); j++) {
                if (dir.startsWith(dirs.get(j)) || dirs.get(j).startsWith(dir)) {
                    throw new InvalidConfigException(at + ": path " + dir + " is the same as or nested with "
                        + "sources[" + j + "].path " + dirs.get(j));
                }
            }
            dirs.add(dir);
            out.add(source);
        }
        return out;
    }

    private static RetentionConfig parseRetention(JsonElement el) {
        if (!el.isJsonObject()) {
            throw new InvalidConfigException("retention must be an object, got " + el);
        }
        JsonObject r = el.getAsJsonObject();
        rejectUnknownKeys(r, RETENTION_KEYS, "retention.");
        Long maxBytes = r.has("maxBytes") ? positiveLong(r.get("maxBytes"), "retention.maxBytes") : null;
        Duration maxAge = null;
        if (r.has("maxAge")) {
            String raw = string(r.get("maxAge"), "retention.maxAge");
            try {
                maxAge = Duration.parse(raw);
            } catch (DateTimeParseException bad) {
                throw new InvalidConfigException("retention.maxAge must be an ISO-8601 duration (e.g. P90D), got '"
                    + raw + "'");
            }
            if (maxAge.isZero() || maxAge.isNegative()) {
                throw new InvalidConfigException("retention.maxAge must be positive, got " + raw);
            }
        }
        return new RetentionConfig(maxAge, maxBytes);
    }

    private static boolean durability(JsonObject obj) {
        if (!obj.has("ackDurability")) {
            return true;
        }
        String v = string(obj.get("ackDurability"), "ackDurability");
        if ("full".equalsIgnoreCase(v)) {
            return true;
        }
        if ("normal".equalsIgnoreCase(v)) {
            return false;
        }
        throw new InvalidConfigException("ackDurability must be 'full' or 'normal', got '" + v + "'");
    }

    private static String suffix(JsonObject obj, String key, String dflt) {
        if (!obj.has(key)) {
            return dflt;
        }
        String v = string(obj.get(key), key);
        if (v.isBlank() || v.contains("/") || v.contains("\\")) {
            throw new InvalidConfigException(key + " must be a non-blank file-name suffix, got '" + v + "'");
        }
        return v;
    }

    private static void rejectUnknownKeys(JsonObject obj, Set<String> known, String prefix) {
        for (String k : obj.keySet()) {
            if (!known.contains(k)) {
                throw new InvalidConfigException("unknown key '" + prefix + k + "' (expected one of " + known + ")");
            }
        }
    }

    private static String requiredString(JsonObject o, String key, String at) {
        if (!o.has(key)) {
            throw new InvalidConfigException(at + "." + key + " is required");
        }
        return string(o.get(key), at + "." + key);
    }

    private static String string(JsonElement el, String key) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw new InvalidConfigException(key + " must be a string, got " + el);
        }
        return el.getAsString();
    }

    private static boolean bool(JsonElement el, String key) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            throw new InvalidConfigException(key + " must be true or false, got " + el);
        }
        return el.getAsBoolean();
    }

    private static int integer(JsonElement el, String key) {
        long v = wholeNumber(el, key);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new InvalidConfigException(key + " is out of range: " + v);
        }
        return (int) v;
    }

    private static long positiveLong(JsonElement el, String key) {
        long v = wholeNumber(el, key);
        if (v <= 0) {
            throw new InvalidConfigException(key + " must be positive, got " + v);
        }
        return v;
    }

    private static long wholeNumber(JsonElement el, String key) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            throw new InvalidConfigException(key + " must be a number, got " + el);
        }
        JsonPrimitive p = el.getAsJsonPrimitive();
        try {
            return new BigDecimal(p.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException notWhole) {
            throw new InvalidConfigException(key + " must be a whole number, got " + p);
        }
    }
}
