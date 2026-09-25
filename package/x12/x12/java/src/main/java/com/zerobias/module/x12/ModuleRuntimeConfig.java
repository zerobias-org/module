package com.zerobias.module.x12;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zerobias.module.x12.buffer.RetentionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
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
 *   "retention": { "maxBytes": 10737418240, "maxAge": "P90D" },
 *   "allowBareTransactionSets": false,
 *   "allowFileManagement": false,
 *   "maxFileBytes": 67108864 }
 * </pre>
 *
 * <p>{@code maxFileBytes} is the largest inbox file read into memory for parsing (default
 * {@value #DEFAULT_MAX_FILE_BYTES}, at most {@value #MAX_MAX_FILE_BYTES}). It is checked from
 * {@code stat} before a byte is read; a bigger file is hashed as a stream (for its identity)
 * and sent to {@code .error} as {@code too-large}, so one oversized drop cannot exhaust the heap
 * and stall every file behind it. A value out of range is clamped with a warning.
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
 * {@code runtimeConfig.yml}) → {@link #defaults()}. Absent/blank/malformed input degrades
 * to safe defaults rather than a boot crash; malformed <em>source entries</em> are
 * skipped with a warning. Directory validation is separate ({@link #validateSources()})
 * because it is fatal by design (DESIGN §3: a daemon that cannot mark files consumed
 * must not run).
 */
public record ModuleRuntimeConfig(
        List<SourceConfig> sources,
        String consumedSuffix,
        String errorSuffix,
        boolean fullDurability,
        RetentionConfig retention,
        boolean allowBareTransactionSets,
        boolean allowFileManagement,
        long maxFileBytes) {

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
     * 128 MiB. A file can be a single transaction set, stored whole as one {@code raw_x12}
     * value while the parser holds its tree and the materializer its graph: several copies of
     * the file in the heap at once, so this is kept well under what the heap can hold.
     */
    public static final long MAX_MAX_FILE_BYTES = 128L * 1024 * 1024;

    public ModuleRuntimeConfig {
        sources = List.copyOf(sources);
        if (maxFileBytes <= 0) {
            maxFileBytes = DEFAULT_MAX_FILE_BYTES;
        } else if (maxFileBytes > MAX_MAX_FILE_BYTES) {
            LOG.warn("maxFileBytes {} is over the {} cap; using the cap", maxFileBytes, MAX_MAX_FILE_BYTES);
            maxFileBytes = MAX_MAX_FILE_BYTES;
        }
    }

    /** Without {@code maxFileBytes}: the default. */
    public ModuleRuntimeConfig(List<SourceConfig> sources, String consumedSuffix, String errorSuffix,
                               boolean fullDurability, RetentionConfig retention,
                               boolean allowBareTransactionSets, boolean allowFileManagement) {
        this(sources, consumedSuffix, errorSuffix, fullDurability, retention, allowBareTransactionSets,
            allowFileManagement, DEFAULT_MAX_FILE_BYTES);
    }

    /** The image defaults (mirror {@code runtimeConfig.yml} minus retention, which is unbounded). */
    public static ModuleRuntimeConfig defaults() {
        return new ModuleRuntimeConfig(
            List.of(new SourceConfig(DEFAULT_SOURCE_NAME, DEFAULT_SOURCE_PATH, DEFAULT_SOURCE_PATTERN,
                SourceConfig.DEFAULT_POLL_INTERVAL_SEC, SourceConfig.DEFAULT_STABLE_FOR_SEC)),
            DEFAULT_CONSUMED_SUFFIX, DEFAULT_ERROR_SUFFIX, true, RetentionConfig.none(), false, false);
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

    /** Parse the {@code MODULE_CONFIG} JSON text; malformed → defaults. */
    public static ModuleRuntimeConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        try {
            JsonElement el = GSON.fromJson(json, JsonElement.class);
            if (el == null || !el.isJsonObject()) {
                return defaults();
            }
            return fromConfigObject(el.getAsJsonObject());
        } catch (RuntimeException malformed) {
            // Malformed MODULE_CONFIG → safe defaults rather than crashing the daemon at
            // boot. A deploy with a typo must not wedge an always-on receiver.
            LOG.warn("MODULE_CONFIG is malformed ({}); using defaults", malformed.toString());
            return defaults();
        }
    }

    /** Build from an already-parsed {@code config} object (env JSON or file block). */
    public static ModuleRuntimeConfig fromConfigObject(JsonObject obj) {
        ModuleRuntimeConfig d = defaults();
        if (obj == null) {
            return d;
        }
        try {
            List<SourceConfig> sources = parseSources(obj);
            if (sources.isEmpty()) {
                sources = d.sources();
            }
            String consumed = str(obj, "consumedSuffix", d.consumedSuffix());
            String error = str(obj, "errorSuffix", d.errorSuffix());
            // full unless explicitly "normal": the rename is the ack, so a commit that a power
            // loss can roll back after the .done rename loses the file for good. An unknown
            // value keeps the safe setting rather than silently weakening it.
            String durability = str(obj, "ackDurability", "full");
            boolean full = !"normal".equalsIgnoreCase(durability);
            if (full && !"full".equalsIgnoreCase(durability)) {
                LOG.warn("ackDurability '{}' is neither full nor normal; using full", durability);
            }
            boolean bare = bool(obj, "allowBareTransactionSets");
            boolean fileMgmt = bool(obj, "allowFileManagement");
            long maxFileBytes = longValue(obj, "maxFileBytes", DEFAULT_MAX_FILE_BYTES);
            if (maxFileBytes <= 0) {
                LOG.warn("maxFileBytes {} is not positive; using the default {}", maxFileBytes, DEFAULT_MAX_FILE_BYTES);
            }
            return new ModuleRuntimeConfig(sources, consumed, error, full, parseRetention(obj), bare, fileMgmt,
                maxFileBytes);
        } catch (RuntimeException malformed) {
            LOG.warn("module config has wrong-typed fields ({}); using defaults", malformed.toString());
            return d;
        }
    }

    /**
     * Boot validation (DESIGN §3): every source path must exist, be a directory and be
     * renameable; names must be distinct, and so must the real directories they point at;
     * suffixes must be non-blank and distinct.
     * Returns the list of problems (empty = OK). The caller logs and exits 1 on any.
     */
    public List<String> validateSources() {
        List<String> problems = new ArrayList<>();
        if (sources.isEmpty()) {
            problems.add("no sources configured");
        }
        Set<String> names = new HashSet<>();
        Map<java.nio.file.Path, String> dirs = new java.util.HashMap<>();
        for (SourceConfig s : sources) {
            if (!names.add(s.name())) {
                problems.add("duplicate source name: " + s.name());
            }
            String p = s.validate();
            if (p != null) {
                problems.add(p);
                continue;
            }
            // Two pollers on one directory would race for every file (both hash it, one renames
            // it from under the other) and stamp it with whichever source won. Compare real
            // paths so a symlink, `..` or a trailing slash cannot hide the overlap. A nested
            // directory is fine: each poller scans its own directory flat.
            try {
                java.nio.file.Path real = s.dir().toRealPath();
                String other = dirs.putIfAbsent(real, s.name());
                if (other != null) {
                    problems.add("sources '" + other + "' and '" + s.name() + "' point at the same directory: " + real);
                }
            } catch (java.io.IOException e) {
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

    private static List<SourceConfig> parseSources(JsonObject obj) {
        List<SourceConfig> out = new ArrayList<>();
        if (!obj.has("sources") || !obj.get("sources").isJsonArray()) {
            return out;
        }
        for (JsonElement el : obj.getAsJsonArray("sources")) {
            if (!el.isJsonObject()) {
                LOG.warn("skipping non-object sources[] entry: {}", el);
                continue;
            }
            JsonObject s = el.getAsJsonObject();
            String name = str(s, "name", null);
            String path = str(s, "path", null);
            if (name == null || name.isBlank() || path == null || path.isBlank()) {
                LOG.warn("skipping sources[] entry with missing name/path: {}", s);
                continue;
            }
            out.add(new SourceConfig(name, path, str(s, "pattern", SourceConfig.DEFAULT_PATTERN),
                integer(s, "pollIntervalSec", SourceConfig.DEFAULT_POLL_INTERVAL_SEC),
                integer(s, "stableForSec", SourceConfig.DEFAULT_STABLE_FOR_SEC)));
        }
        return out;
    }

    private static RetentionConfig parseRetention(JsonObject obj) {
        if (!obj.has("retention") || !obj.get("retention").isJsonObject()) {
            return RetentionConfig.none();
        }
        JsonObject r = obj.getAsJsonObject("retention");
        Long maxBytes = (r.has("maxBytes") && r.get("maxBytes").isJsonPrimitive()
                && r.getAsJsonPrimitive("maxBytes").isNumber())
            ? r.get("maxBytes").getAsLong() : null;
        Duration maxAge = null;
        if (r.has("maxAge") && r.get("maxAge").isJsonPrimitive()) {
            try {
                maxAge = Duration.parse(r.get("maxAge").getAsString());
            } catch (java.time.format.DateTimeParseException badDuration) {
                // A bad maxAge disables age-based eviction only; it must not discard
                // maxBytes or the rest of the config.
                LOG.warn("retention.maxAge '{}' is not ISO-8601; age-based eviction disabled",
                    r.get("maxAge").getAsString());
            }
        }
        return new RetentionConfig(maxAge, maxBytes);
    }

    private static String str(JsonObject o, String key, String dflt) {
        if (o.has(key) && o.get(key).isJsonPrimitive()) {
            String v = o.get(key).getAsString();
            return v.isBlank() ? dflt : v;
        }
        return dflt;
    }

    /** A strict boolean flag: absent, non-boolean or false all mean false (opt-in only). */
    private static boolean bool(JsonObject o, String key) {
        return o.has(key)
            && o.get(key).isJsonPrimitive()
            && o.getAsJsonPrimitive(key).isBoolean()
            && o.get(key).getAsBoolean();
    }

    private static long longValue(JsonObject o, String key, long dflt) {
        if (o.has(key) && o.get(key).isJsonPrimitive() && o.getAsJsonPrimitive(key).isNumber()) {
            return o.get(key).getAsLong();
        }
        return dflt;
    }

    private static int integer(JsonObject o, String key, int dflt) {
        if (o.has(key) && o.get(key).isJsonPrimitive() && o.getAsJsonPrimitive(key).isNumber()) {
            return o.get(key).getAsInt();
        }
        return dflt;
    }
}
