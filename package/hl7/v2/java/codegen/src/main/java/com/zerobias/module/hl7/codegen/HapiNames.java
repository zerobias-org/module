package com.zerobias.module.hl7.codegen;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives DataProducer property names from HAPI's generated accessor methods.
 *
 * <p>HAPI generates, for each segment field / composite component, a positional
 * accessor whose name encodes the 1-based index and the bean name, e.g.
 * {@code getPid3_PatientIdentifierList()} or {@code getCx1_IDNumber()}. Parsing
 * those gives an authoritative {index → beanName} map, so the schema property
 * names match exactly what a HAPI Java client sees (DESIGN §2.3) — the contract
 * consumers traverse. Bean names use the JavaBeans decapitalize rule, so
 * {@code IDNumber} stays {@code IDNumber} (leading acronym preserved).
 */
public final class HapiNames {

    // get <Prefix> <number> _ <BeanName>   e.g. getPid3_PatientIdentifierList
    private static final Pattern POSITIONAL_ACCESSOR =
        Pattern.compile("^get([A-Za-z]+?)(\\d+)_([A-Za-z0-9]+)$");

    private HapiNames() {
    }

    /** A parsed positional accessor: the 1-based HL7 index and the bean property name. */
    public record Accessor(int index, String beanName) {
    }

    /** Parse a HAPI positional accessor method name, if it is one. */
    public static Optional<Accessor> parseAccessor(String methodName) {
        if (methodName == null) {
            return Optional.empty();
        }
        final Matcher m = POSITIONAL_ACCESSOR.matcher(methodName);
        if (!m.matches()) {
            return Optional.empty();
        }
        final int index = Integer.parseInt(m.group(2));
        return Optional.of(new Accessor(index, decapitalize(m.group(3))));
    }

    /**
     * HAPI's method-name prefix for a structure simple name: capitalize the first
     * char, lowercase the rest ({@code "GT1" -> "Gt1"}, {@code "PID" -> "Pid"},
     * {@code "CX" -> "Cx"}). This mirrors how HAPI builds {@code get<Prefix><N>_<Bean>}.
     */
    public static String accessorPrefix(String structureSimpleName) {
        if (structureSimpleName == null || structureSimpleName.isEmpty()) {
            return structureSimpleName;
        }
        return Character.toUpperCase(structureSimpleName.charAt(0))
            + structureSimpleName.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Parse a positional accessor when the structure's <em>exact</em> accessor
     * prefix is known. The generic {@link #parseAccessor(String)} guesses the
     * alpha/number split, which is ambiguous for segments whose name ends in a
     * digit ({@code getGt11_...} is GT1 field 1, not a "Gt" field 11) — folding the
     * trailing digit into the index and colliding fields 1-9 with 11-19. Anchoring
     * on the known prefix removes the ambiguity.
     */
    public static Optional<Accessor> parseAccessor(String methodName, String accessorPrefix) {
        if (methodName == null || accessorPrefix == null || accessorPrefix.isEmpty()) {
            return Optional.empty();
        }
        final Matcher m = Pattern
            .compile("^get" + Pattern.quote(accessorPrefix) + "(\\d+)_([A-Za-z0-9]+)$")
            .matcher(methodName);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Accessor(Integer.parseInt(m.group(1)), decapitalize(m.group(2))));
    }

    /**
     * JavaBeans decapitalization (java.beans.Introspector rule): lowercase the
     * first char, UNLESS the first two chars are both uppercase (acronym), in
     * which case leave unchanged. {@code "PatientName" → "patientName"},
     * {@code "IDNumber" → "IDNumber"}.
     */
    public static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() > 1
                && Character.isUpperCase(name.charAt(0))
                && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        final char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    /**
     * Fallback bean name from an HL7 descriptive field name (e.g. HAPI's
     * {@code getNames()} entries like {@code "Patient Identifier List"}), used
     * only when no positional accessor is found. Splits on non-alphanumerics,
     * lowercases the first token, capitalizes the rest:
     * {@code "Patient Identifier List" → "patientIdentifierList"},
     * {@code "Set ID - PID" → "setIDPID"}.
     */
    public static String fromDescription(String description) {
        if (description == null || description.isBlank()) {
            return description;
        }
        final String[] tokens = description.trim().split("[^A-Za-z0-9]+");
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            final String t = tokens[i];
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() == 0) {
                // First token: lowercase unless it is an all-caps acronym.
                sb.append(t.equals(t.toUpperCase()) ? t : t.substring(0, 1).toLowerCase() + t.substring(1));
            } else {
                sb.append(Character.toUpperCase(t.charAt(0))).append(t.substring(1));
            }
        }
        return sb.toString();
    }
}
