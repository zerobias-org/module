package com.zerobias.module.x12;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.zerobias.module.x12.buffer.RetentionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.file.FileSystems;
import java.time.Duration;
import java.time.format.DateTimeParseException;
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
 * and stall every file behind it.
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
 * present at all, and a present config's missing keys take their defaults. A config that is
 * present but wrong — malformed JSON, an unknown key, a wrong-typed or out-of-range value, an
 * unusable {@code sources[]} entry — throws {@link InvalidConfigException}, and the daemon
 * exits 1 at boot. Falling back instead hides the mistake: a typo'd {@code retention} silently
 * disables eviction, a misspelt source watches the default directory, a bad
 * {@code ackDurability} drops the durability the operator asked for — and nothing shows it but
 * an unexpected quiet. Directory validation is separate ({@link #validateSources()}) because
 * it touches the filesystem; it is fatal too (DESIGN §3: a daemon that cannot mark files
 * consumed must not run).
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

    /** Every key {@code config} may carry; anything else is a typo, and a typo is fatal. */
    static final Set<String> KEYS = Set.of("sources", "consumedSuffix", "errorSuffix", "ackDurability",
        "retention", "allowBareTransactionSets", "allowFileManagement", "maxFileBytes");
    static final Set<String> SOURCE_KEYS = Set.of("name", "path", "pattern", "pollIntervalSec", "stableForSec");
    static final Set<String> RETENTION_KEYS = Set.of("maxBytes", "maxAge");

    /** A present-but-unusable module config: fatal at boot (the process exits 1). */
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

    /** Parse the {@code MODULE_CONFIG} JSON text; null/blank is absent (defaults), anything else must be valid. */
    public static ModuleRuntimeConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        final JsonElement el;
        try {
            el = GSON.fromJson(json, JsonElement.class);
        } catch (JsonParseException malformed) {
            // The position only: the parser's message can name what it read, and the value of
            // MODULE_CONFIG stays out of the log (startup.sh prints only whether it is set).
            java.util.regex.Matcher at = java.util.regex.Pattern.compile("line \\d+ column \\d+")
                .matcher(String.valueOf(malformed.getMessage()));
            throw new InvalidConfigException("MODULE_CONFIG is not valid JSON" + (at.find() ? " (at " + at.group() + ")" : ""));
        }
        if (el == null || !el.isJsonObject()) {
            throw new InvalidConfigException("MODULE_CONFIG must be a JSON object, got " + kind(el));
        }
        return fromConfigObject(el.getAsJsonObject());
    }

    /**
     * Build from an already-parsed {@code config} object (env JSON or file block). Every key is
     * optional and a missing one takes its default; a key that is present must be known and
     * well-formed, or this throws {@link InvalidConfigException}.
     */
    public static ModuleRuntimeConfig fromConfigObject(JsonObject obj) {
        if (obj == null) {
            return defaults();
        }
        rejectUnknownKeys(obj, KEYS, "");
        final ModuleRuntimeConfig d = defaults();
        final List<SourceConfig> sources = obj.has("sources") ? parseSources(obj.get("sources")) : d.sources();
        final String consumed = obj.has("consumedSuffix") ? suffix(obj.get("consumedSuffix"), "consumedSuffix")
            : d.consumedSuffix();
        final String error = obj.has("errorSuffix") ? suffix(obj.get("errorSuffix"), "errorSuffix") : d.errorSuffix();
        // full unless explicitly "normal": the rename is the ack, so a commit that a power loss can
        // roll back after the .done rename loses the file for good.
        final boolean full = !obj.has("ackDurability") || durability(obj.get("ackDurability"));
        final RetentionConfig retention = obj.has("retention") ? parseRetention(obj.get("retention"))
            : RetentionConfig.none();
        // Only a literal true opens either: a string, a number or a null is a mistake, not a "no".
        final boolean bare = obj.has("allowBareTransactionSets")
            && bool(obj.get("allowBareTransactionSets"), "allowBareTransactionSets");
        final boolean fileMgmt = obj.has("allowFileManagement")
            && bool(obj.get("allowFileManagement"), "allowFileManagement");
        final long maxFileBytes = obj.has("maxFileBytes")
            ? wholeNumber(obj.get("maxFileBytes"), "maxFileBytes", 1, MAX_MAX_FILE_BYTES) : DEFAULT_MAX_FILE_BYTES;
        return new ModuleRuntimeConfig(sources, consumed, error, full, retention, bare, fileMgmt, maxFileBytes);
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
            String p = s.validate(consumedSuffix, errorSuffix);
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

    // --- parsing helpers (strict: every failure is an InvalidConfigException) --------

    private static List<SourceConfig> parseSources(JsonElement el) {
        if (el == null || !el.isJsonArray() || el.getAsJsonArray().isEmpty()) {
            throw new InvalidConfigException("sources must be a non-empty array, got " + kind(el));
        }
        final List<SourceConfig> out = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        int i = 0;
        for (JsonElement entry : el.getAsJsonArray()) {
            final String at = "sources[" + i++ + "]";
            if (!entry.isJsonObject()) {
                throw new InvalidConfigException(at + " must be an object, got " + kind(entry));
            }
            final JsonObject s = entry.getAsJsonObject();
            rejectUnknownKeys(s, SOURCE_KEYS, at + ".");
            final String name = requiredString(s, "name", at);
            final String path = requiredString(s, "path", at);
            String pattern = SourceConfig.DEFAULT_PATTERN;
            if (s.has("pattern")) {
                pattern = string(s.get("pattern"), at + ".pattern");
                if (pattern.isBlank()) {
                    throw new InvalidConfigException(at + ".pattern must not be blank");
                }
                try {
                    // A glob that does not compile would throw on every scan instead of once here.
                    FileSystems.getDefault().getPathMatcher("glob:" + pattern.toLowerCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException badGlob) {
                    throw new InvalidConfigException(at + ".pattern is not a valid glob: '" + pattern + "'");
                }
            }
            // SourceConfig quietly replaces a non-positive cadence with its default; a config that
            // asks for one is a mistake to report, not a value to correct.
            final int poll = s.has("pollIntervalSec")
                ? (int) wholeNumber(s.get("pollIntervalSec"), at + ".pollIntervalSec", 1, Integer.MAX_VALUE)
                : SourceConfig.DEFAULT_POLL_INTERVAL_SEC;
            final int stable = s.has("stableForSec")
                ? (int) wholeNumber(s.get("stableForSec"), at + ".stableForSec", 0, Integer.MAX_VALUE)
                : SourceConfig.DEFAULT_STABLE_FOR_SEC;
            if (!names.add(name)) {
                throw new InvalidConfigException(at + ": duplicate source name '" + name + "'");
            }
            out.add(new SourceConfig(name, path, pattern, poll, stable));
        }
        return out;
    }

    private static RetentionConfig parseRetention(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            throw new InvalidConfigException("retention must be an object, got " + kind(el));
        }
        final JsonObject r = el.getAsJsonObject();
        rejectUnknownKeys(r, RETENTION_KEYS, "retention.");
        final Long maxBytes = r.has("maxBytes")
            ? wholeNumber(r.get("maxBytes"), "retention.maxBytes", 1, Long.MAX_VALUE) : null;
        Duration maxAge = null;
        if (r.has("maxAge")) {
            final String raw = string(r.get("maxAge"), "retention.maxAge");
            try {
                maxAge = Duration.parse(raw);
            } catch (DateTimeParseException bad) {
                throw new InvalidConfigException("retention.maxAge must be an ISO-8601 duration such as P90D, got '"
                    + raw + "'");
            }
            if (maxAge.isZero() || maxAge.isNegative()) {
                throw new InvalidConfigException("retention.maxAge must be positive, got '" + raw + "'");
            }
        }
        return new RetentionConfig(maxAge, maxBytes);
    }

    /** {@code full} (true) or {@code normal} (false), any case; anything else is refused, never guessed. */
    private static boolean durability(JsonElement el) {
        final String v = string(el, "ackDurability");
        if ("full".equalsIgnoreCase(v)) {
            return true;
        }
        if ("normal".equalsIgnoreCase(v)) {
            return false;
        }
        throw new InvalidConfigException("ackDurability must be 'full' or 'normal', got '" + v + "'");
    }

    /** A file-name suffix: a separator in it would rename files out of their directory. */
    private static String suffix(JsonElement el, String key) {
        final String v = string(el, key);
        if (v.isBlank() || v.contains("/") || v.contains("\\")) {
            throw new InvalidConfigException(key + " must be a non-blank file-name suffix without a path separator, "
                + "got '" + v + "'");
        }
        return v;
    }

    private static void rejectUnknownKeys(JsonObject obj, Set<String> known, String prefix) {
        for (String k : obj.keySet()) {
            if (!known.contains(k)) {
                throw new InvalidConfigException("unknown key '" + prefix + k + "' (expected one of "
                    + new java.util.TreeSet<>(known) + ")");
            }
        }
    }

    private static String requiredString(JsonObject o, String key, String at) {
        if (!o.has(key)) {
            throw new InvalidConfigException(at + "." + key + " is required");
        }
        final String v = string(o.get(key), at + "." + key);
        if (v.isBlank()) {
            throw new InvalidConfigException(at + "." + key + " must not be blank");
        }
        return v;
    }

    private static String string(JsonElement el, String key) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw new InvalidConfigException(key + " must be a string, got " + kind(el));
        }
        return el.getAsString();
    }

    private static boolean bool(JsonElement el, String key) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            throw new InvalidConfigException(key + " must be true or false, got " + kind(el));
        }
        return el.getAsBoolean();
    }

    /** A JSON number that is a whole value within {@code [min, max]}. */
    private static long wholeNumber(JsonElement el, String key, long min, long max) {
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            throw new InvalidConfigException(key + " must be a number, got " + kind(el));
        }
        final JsonPrimitive p = el.getAsJsonPrimitive();
        final long v;
        try {
            v = new BigDecimal(p.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException notWhole) {
            throw new InvalidConfigException(key + " must be a whole number, got " + p.getAsString());
        }
        if (v < min || v > max) {
            throw new InvalidConfigException(key + " must be between " + min + " and " + max + ", got " + v);
        }
        return v;
    }

    /**
     * What a value is, for an error message: a type error names the JSON type, not the value, so
     * a misplaced value (a path, or whatever a future field carries) is not echoed into the log.
     */
    private static String kind(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return "null";
        }
        if (el.isJsonObject()) {
            return "an object";
        }
        if (el.isJsonArray()) {
            return el.getAsJsonArray().isEmpty() ? "an empty array" : "an array";
        }
        final JsonPrimitive p = el.getAsJsonPrimitive();
        return p.isBoolean() ? "a boolean" : p.isNumber() ? "a number" : "a string";
    }
}
