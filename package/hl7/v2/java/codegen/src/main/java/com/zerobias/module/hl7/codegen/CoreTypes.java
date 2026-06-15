package com.zerobias.module.hl7.codegen;

import com.zerobias.module.hl7.codegen.model.DataType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps HL7 v2 primitive datatypes to DataProducer core dataTypes (DESIGN §2.4)
 * and builds the {@code dataTypes[]} entries. Composite datatypes are NOT mapped
 * here — they become composition {@code references} to their own
 * {@code schema:type:} (handled by the walker).
 *
 * <p>Rule of thumb (CoreDataTypes.md): never {@code string + format} when a core
 * type exists. Clinical numerics (NM) are {@code decimal}, never float.
 */
public final class CoreTypes {

    public static final String STRING = "string";
    public static final String INTEGER = "integer";
    public static final String DECIMAL = "decimal";
    public static final String DATE = "date";
    public static final String DATE_TIME = "date-time";

    /** HL7 primitive simple class name → core dataType name. */
    private static final Map<String, String> PRIMITIVE = Map.ofEntries(
        Map.entry("ST", STRING),
        Map.entry("TX", STRING),
        Map.entry("FT", STRING),
        Map.entry("GTS", STRING),
        Map.entry("NULLDT", STRING),
        Map.entry("ID", STRING),     // table-bound — also gets an enum reference
        Map.entry("IS", STRING),     // table-bound — also gets an enum reference
        Map.entry("NM", DECIMAL),    // clinical values — never float
        Map.entry("SI", INTEGER),
        Map.entry("DT", DATE),
        Map.entry("TM", STRING),     // no core time-only type; format: time hint
        Map.entry("DTM", DATE_TIME),
        Map.entry("TS", DATE_TIME)
    );

    /** Table-bound primitives that additionally carry a {@code schema:enum:} reference. */
    private static final Set<String> ENUM_BOUND = Set.of("ID", "IS");

    private CoreTypes() {
    }

    /** Core dataType for an HL7 primitive simple name; unknown primitives fall back to string. */
    public static String forPrimitive(String hl7PrimitiveSimpleName) {
        return PRIMITIVE.getOrDefault(hl7PrimitiveSimpleName, STRING);
    }

    public static boolean isEnumBound(String hl7PrimitiveSimpleName) {
        return ENUM_BOUND.contains(hl7PrimitiveSimpleName);
    }

    /** The {@code format} hint for primitives that need one (TM → time), else null. */
    public static String formatHint(String hl7PrimitiveSimpleName) {
        return "TM".equals(hl7PrimitiveSimpleName) ? "time" : null;
    }

    /** Build the core DataType definition for a core type name (for the schema's dataTypes[]). */
    public static DataType definition(String coreType) {
        switch (coreType) {
            case STRING:
                return new DataType(STRING, "string", "Text values",
                    list("example text", "hello world"), "text");
            case INTEGER:
                return new DataType(INTEGER, "number", "Integer numbers",
                    list(42, -100, 999), "number");
            case DECIMAL:
                return new DataType(DECIMAL, "number",
                    "Decimal numbers for currency and precise calculations",
                    list(19.99, 100.50, -25.75), "number");
            case DATE:
                return new DataType(DATE, "string", "ISO 8601 dates (YYYY-MM-DD)",
                    list("2025-10-29", "2024-01-15"), "date");
            case DATE_TIME:
                return new DataType(DATE_TIME, "string", "ISO 8601 timestamps",
                    list("2025-10-29T10:30:00.000Z", "2024-01-15T14:22:15Z"), "datetime-local");
            default:
                throw new IllegalArgumentException("no core DataType definition for: " + coreType);
        }
    }

    private static List<Object> list(Object... values) {
        return Arrays.asList(values);
    }
}
