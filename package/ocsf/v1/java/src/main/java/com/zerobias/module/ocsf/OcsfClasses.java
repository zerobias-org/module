package com.zerobias.module.ocsf;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The OCSF event classes this receiver serves, read from the interface's own {@code api.yml}
 * ({@code @zerobias-org/module-interface-ocsf}, bundled into the jar at build — see pom.xml):
 * every model carrying {@code x-ocsf-class-uid}. It is the one table both sides of the module
 * use: ingest accepts an event only when its {@code class_uid} is here (DESIGN §4), and the
 * generic handler resolves {@code IamApi.listAuthentication} → model {@code Authentication} →
 * class 3002 through it (DESIGN §2). A new OCSF version is a dependency bump, no code.
 */
public final class OcsfClasses {

    /** One class as {@code listClasses} returns it, plus the model name its operations carry. */
    public record OcsfClass(int uid, String name, String caption, Integer categoryUid, String category, String model) {
    }

    static final String RESOURCE = "/ocsf/api.yml";

    private final String ocsfVersion;
    private final Map<Integer, OcsfClass> byUid;
    private final Map<String, OcsfClass> byModel;

    private OcsfClasses(String ocsfVersion, Map<Integer, OcsfClass> byUid, Map<String, OcsfClass> byModel) {
        this.ocsfVersion = ocsfVersion;
        this.byUid = Collections.unmodifiableMap(byUid);
        this.byModel = Collections.unmodifiableMap(byModel);
    }

    /** The table from the bundled interface spec; fails fast when the jar was built without it. */
    public static OcsfClasses fromClasspath() {
        try (InputStream in = OcsfClasses.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + RESOURCE
                    + ": build with @zerobias-org/module-interface-ocsf installed (npm install)");
            }
            return parse(in);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + RESOURCE, e);
        }
    }

    @SuppressWarnings("unchecked")
    static OcsfClasses parse(InputStream in) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(64 * 1024 * 1024);   // the spec is a few MB; snakeyaml's default cap is 3 MB
        Map<String, Object> spec = new Yaml(options).load(in);
        Map<String, Object> info = (Map<String, Object>) spec.getOrDefault("info", Map.of());
        Map<String, Object> components = (Map<String, Object>) spec.getOrDefault("components", Map.of());
        Map<String, Object> schemas = (Map<String, Object>) components.getOrDefault("schemas", Map.of());
        Map<Integer, OcsfClass> byUid = new TreeMap<>();
        Map<String, OcsfClass> byModel = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : schemas.entrySet()) {
            Map<String, Object> model = (Map<String, Object>) e.getValue();
            Object uid = model.get("x-ocsf-class-uid");
            if (!(uid instanceof Number n)) {
                continue;   // an object, not an event class
            }
            OcsfClass c = new OcsfClass(n.intValue(), (String) model.get("x-ocsf-name"),
                (String) model.get("x-ocsf-caption"), intOrNull(model.get("x-ocsf-category-uid")),
                (String) model.get("x-ocsf-category"), e.getKey());
            byUid.put(c.uid(), c);
            byModel.put(c.model(), c);
        }
        if (byUid.isEmpty()) {
            throw new IllegalStateException(RESOURCE + " declares no OCSF event classes (x-ocsf-class-uid)");
        }
        return new OcsfClasses(String.valueOf(info.get("x-ocsf-version")), byUid, byModel);
    }

    private static Integer intOrNull(Object v) {
        return v instanceof Number n ? n.intValue() : null;
    }

    public String ocsfVersion() {
        return ocsfVersion;
    }

    public Optional<OcsfClass> byUid(int classUid) {
        return Optional.ofNullable(byUid.get(classUid));
    }

    /** The class whose operations carry this model name ({@code listAuthentication} → {@code Authentication}). */
    public Optional<OcsfClass> byModel(String modelName) {
        return Optional.ofNullable(byModel.get(modelName));
    }

    public Collection<OcsfClass> all() {
        return byUid.values();
    }
}
