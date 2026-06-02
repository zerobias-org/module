package com.zerobias.module.hl7.ext;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * An extension pack's {@code extensions/manifest.json} (DESIGN §7.1/§7.2). The
 * module reads this at boot for version compatibility, the schema {@code namespace},
 * and the {@link Discriminator} routing rules. Shape is module-defined (the
 * platform never parses it):
 *
 * <pre>
 * {
 *   "namespace": "epic",
 *   "hl7Version": "2.5.1",
 *   "vendor": "epic",
 *   "discriminators": [
 *     { "messageCode": "ADT", "senderEquals": "EPIC", "structure": "ADT_A01_with_ZPV" }
 *   ]
 * }
 * </pre>
 */
public final class ExtensionManifest {

    private static final Gson GSON = new Gson();

    public String namespace;
    public String hl7Version;
    public String vendor;
    public List<DiscriminatorRule> discriminators = new ArrayList<>();

    public static final class DiscriminatorRule {
        public String messageCode;
        public String senderEquals;
        public String structure;
    }

    public static ExtensionManifest fromFile(Path manifestJson) {
        try {
            ExtensionManifest m = GSON.fromJson(Files.readString(manifestJson), ExtensionManifest.class);
            if (m == null || m.namespace == null || m.namespace.isBlank()) {
                throw new IllegalStateException("manifest missing namespace: " + manifestJson);
            }
            return m;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read extension manifest " + manifestJson, e);
        }
    }

    /** Resolve this manifest's rules into {@link Discriminator}s under its namespace. */
    public List<Discriminator> discriminators() {
        List<Discriminator> out = new ArrayList<>();
        for (DiscriminatorRule r : discriminators) {
            out.add(new Discriminator(namespace, r.messageCode, r.senderEquals, r.structure));
        }
        return out;
    }
}
