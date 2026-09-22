package com.zerobias.module.x12.parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The committed synthetic fixtures ({@code src/test/resources/fixtures}, see its README). */
public final class Fixtures {

    public static final Path DIR = Path.of("src/test/resources/fixtures");
    public static final Path MALFORMED = DIR.resolve("malformed");

    public static final String F835 = "835-005010X221A1.x12";
    public static final String F837P = "837P-005010X222A1.x12";
    public static final String F837I = "837I-005010X223A2.x12";
    public static final String F277CA = "277CA-005010X214.x12";
    public static final String F999 = "999-005010X231A1.x12";

    private Fixtures() {
    }

    public static byte[] bytes(String name) {
        try {
            return Files.readAllBytes(DIR.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String text(String name) {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }

    public static byte[] malformed(String name) {
        try {
            return Files.readAllBytes(MALFORMED.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The 835 fixture's ST..SE segments only (no ISA/GS/GE/IEA), one per line, for the bare-ST path. */
    public static String bare835WithSt03() {
        StringBuilder sb = new StringBuilder();
        boolean in = false;
        for (String line : text(F835).split("\n")) {
            if (line.startsWith("ST*")) {
                in = true;
                // 835 STs carry no ST03 on the wire; the synthesizer needs one to know the guide.
                sb.append("ST*835*0001*005010X221A1~\n");
                continue;
            }
            if (line.startsWith("GE*")) {
                in = false;
            }
            if (in) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
