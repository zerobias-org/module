package com.zerobias.module.x12.codegen;

import java.util.Locale;

/**
 * Property naming for loops, segments and elements (DESIGN §2.3): pyx12 xids
 * lower-camel-cased where they are words and verbatim (prefixed) where they are
 * codes, so every key is a legal JavaScript identifier and the materialized JSON
 * uses exactly the same keys as the schema.
 *
 * <ul>
 *   <li>loop {@code 2100} → {@code loop2100}; {@code 1000A} → {@code loop1000A};
 *       {@code HEADER} → {@code header}; {@code ISA_LOOP} → {@code isaLoop}</li>
 *   <li>segment {@code CLP} → {@code clp}; {@code NM1} → {@code nm1}</li>
 *   <li>element {@code CLP02} → {@code clp02} (segment property + two-digit position)</li>
 *   <li>composite sub-element: composite {@code C003} seq 1 → {@code c00301}
 *       (composites are shared across segments, so the position-bound xid
 *       {@code SVC01-01} cannot be the key)</li>
 * </ul>
 */
public final class Names {

    private Names() {
    }

    public static String loopProperty(String loopXid) {
        if (loopXid.isEmpty()) {
            throw new IllegalArgumentException("empty loop xid");
        }
        if (Character.isDigit(loopXid.charAt(0))) {
            return "loop" + loopXid;
        }
        return lowerCamel(loopXid);
    }

    public static String segmentProperty(String segmentXid) {
        return segmentXid.toLowerCase(Locale.ROOT);
    }

    public static String compositeElementProperty(String compositeDataEle, int seq) {
        return compositeDataEle.toLowerCase(Locale.ROOT) + String.format("%02d", seq);
    }

    /** {@code ISA_LOOP} → {@code isaLoop}, {@code HEADER} → {@code header}. */
    static String lowerCamel(String word) {
        final StringBuilder sb = new StringBuilder();
        boolean upNext = false;
        for (char ch : word.toCharArray()) {
            if (!Character.isLetterOrDigit(ch)) {
                upNext = sb.length() > 0;
                continue;
            }
            if (upNext) {
                sb.append(Character.toUpperCase(ch));
                upNext = false;
            } else {
                sb.append(Character.toLowerCase(ch));
            }
        }
        return sb.toString();
    }
}
