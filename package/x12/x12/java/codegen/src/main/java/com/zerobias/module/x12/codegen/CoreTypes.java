package com.zerobias.module.x12.codegen;

import com.zerobias.module.x12.codegen.model.DataType;

import java.util.Arrays;
import java.util.List;

/**
 * Maps pyx12 {@code dataele.xml} data types to DataProducer core dataTypes
 * (DESIGN §2.4) and builds the {@code dataTypes[]} entries. Composites are NOT
 * mapped here — they become composition {@code references} to their own
 * {@code schema:type:} (handled by the walker).
 *
 * <p>Rule of thumb (CoreDataTypes.md): never {@code string + format} when a core
 * type exists. Money and every other numeric is {@code decimal}, never float.
 */
public final class CoreTypes {

    public static final String STRING = "string";
    public static final String INTEGER = "integer";
    public static final String DECIMAL = "decimal";
    public static final String BOOLEAN = "boolean";
    public static final String DATE = "date";
    public static final String DATE_TIME = "date-time";
    public static final String BYTE = "byte";
    public static final String MIME_TYPE = "mimeType";

    private CoreTypes() {
    }

    /**
     * Core dataType for a pyx12 data type ({@code AN ID N0..N9 R DT TM B}).
     * Unknown / missing types fall back to string (with a warning from the walker).
     */
    public static String forX12(String x12Type) {
        if (x12Type == null || x12Type.isBlank()) {
            return STRING;
        }
        switch (x12Type) {
            case "AN":
            case "ID":
            case "TM":
                return STRING;
            case "R":
                return DECIMAL;
            case "DT":
                return DATE;
            case "B":
                return BYTE;
            default:
                if (isImpliedDecimal(x12Type)) {
                    return DECIMAL;
                }
                return STRING;
        }
    }

    /** {@code N0}..{@code N9}: numeric with implied decimal places. */
    public static boolean isImpliedDecimal(String x12Type) {
        return x12Type != null && x12Type.length() == 2 && x12Type.charAt(0) == 'N'
            && Character.isDigit(x12Type.charAt(1));
    }

    /** The implied decimal places for {@code N<n>}, else null. */
    public static Integer impliedDecimals(String x12Type) {
        return isImpliedDecimal(x12Type) ? Integer.valueOf(x12Type.charAt(1) - '0') : null;
    }

    /** The {@code format} hint for types that need one (TM → time), else null. */
    public static String formatHint(String x12Type) {
        return "TM".equals(x12Type) ? "time" : null;
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
            case BOOLEAN:
                return new DataType(BOOLEAN, "boolean", "A boolean value",
                    list(Boolean.TRUE, Boolean.FALSE), "text");
            case DATE:
                return new DataType(DATE, "string", "ISO 8601 dates (YYYY-MM-DD)",
                    list("2025-10-29", "2024-01-15"), "date");
            case DATE_TIME:
                return new DataType(DATE_TIME, "string", "ISO 8601 timestamps",
                    list("2025-10-29T10:30:00.000Z", "2024-01-15T14:22:15Z"), "datetime-local");
            case BYTE:
                return new DataType(BYTE, "string", "Base64-encoded binary data",
                    list("Zm9vYmFyCg==", "SGVsbG8sIHdvcmxkCg=="), "text");
            case MIME_TYPE:
                return new DataType(MIME_TYPE, "string", "A valid MIME type",
                    list("application/EDI-X12", "application/json"), "text");
            default:
                throw new IllegalArgumentException("no core DataType definition for: " + coreType);
        }
    }

    private static List<Object> list(Object... values) {
        return Arrays.asList(values);
    }
}
