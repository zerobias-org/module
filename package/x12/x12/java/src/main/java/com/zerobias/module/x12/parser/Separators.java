package com.zerobias.module.x12.parser;

import java.util.regex.Pattern;

/**
 * The four X12 delimiters of one interchange, read from the fixed-width ISA header:
 * element (ISA char 4), repetition (ISA11, char 83), component (ISA16, char 105) and the
 * segment terminator (char 106). imsweb's {@code Separators} knows only three (no
 * repetition separator), so the materializer uses this record to split {@code ^}-repeated
 * elements itself. {@code lineBreak} is whatever followed the first segment terminator in
 * the file ({@code ""}, {@code "\n"} or {@code "\r\n"}) so reconstructed text keeps the
 * file's line style.
 */
public record Separators(char element, char repetition, char component, char segment, String lineBreak) {

    /** ISA is exactly this many characters, terminator included (DESIGN §4.2 step 3b, imsweb). */
    public static final int ISA_LENGTH = 106;

    public static final Separators DEFAULT = new Separators('*', '^', ':', '~', "");

    /** Where the element separator sits in the fixed-width ISA: before each of ISA01..ISA16. */
    private static final int[] ISA_ELEMENT_OFFSETS = {3, 6, 17, 20, 31, 34, 50, 53, 69, 76, 81, 83, 89, 99, 101, 103};

    /** ISA11 in a 4010 interchange: a standards identifier, not a repetition separator. */
    private static final char NO_REPETITION = 'U';

    /**
     * Read the delimiters from text that starts with an ISA segment. Throws {@code bad-isa} when
     * the text is too short, the element separator is not at every fixed ISA position (a padded
     * field one character short shifts ISA16 and the terminator, and every later segment would
     * split wrongly), or a delimiter is a letter, digit or blank or collides with another one.
     */
    public static Separators fromIsa(String text) throws X12ParseException {
        if (text == null || !text.startsWith("ISA")) {
            throw new X12ParseException("no-isa: file does not start with an ISA segment");
        }
        if (text.length() < ISA_LENGTH) {
            throw new X12ParseException("bad-isa: ISA header is shorter than " + ISA_LENGTH + " characters ("
                + text.length() + ")");
        }
        char element = text.charAt(3);
        char repetition = text.charAt(82);
        char component = text.charAt(104);
        char segment = text.charAt(105);
        for (int at : ISA_ELEMENT_OFFSETS) {
            if (text.charAt(at) != element) {
                throw new X12ParseException("bad-isa: element separator '" + element + "' expected at ISA character "
                    + (at + 1) + " but found '" + text.charAt(at) + "'; the ISA is not " + ISA_LENGTH + " fixed-width characters");
            }
        }
        boolean hasRepetition = repetition != NO_REPETITION;
        if (isDataChar(element) || isDataChar(component) || isDataChar(segment) || (hasRepetition && isDataChar(repetition))) {
            throw new X12ParseException("bad-isa: ISA declares an alphanumeric or blank delimiter" + describe(element,
                repetition, component, segment));
        }
        if (element == component || element == segment || component == segment
                || (hasRepetition && (repetition == element || repetition == component || repetition == segment))) {
            throw new X12ParseException("bad-isa: ISA delimiters are not distinct" + describe(element, repetition,
                component, segment));
        }
        String[] isaTokens = text.substring(0, ISA_LENGTH - 1).split(Pattern.quote(String.valueOf(element)), -1);
        if (isaTokens.length != 17) {
            throw new X12ParseException("bad-isa: ISA header splits into " + (isaTokens.length - 1)
                + " elements with declared element separator '" + element + "', expected 16");
        }
        String lineBreak = "";
        if (text.startsWith("\r\n", ISA_LENGTH)) {
            lineBreak = "\r\n";
        } else if (text.startsWith("\n", ISA_LENGTH)) {
            lineBreak = "\n";
        }
        return new Separators(element, repetition, component, segment, lineBreak);
    }

    private static String describe(char element, char repetition, char component, char segment) {
        return " (element='" + element + "', repetition='" + repetition + "', component='" + component
            + "', segment='" + segment + "')";
    }

    /** True when the char cannot be a delimiter (letters, digits, whitespace other than a line break). */
    static boolean isDataChar(char c) {
        return Character.isLetterOrDigit(c) || (Character.isWhitespace(c) && c != '\n' && c != '\r');
    }

    /** True when both carry the same four delimiters (the line style may differ). */
    public boolean sameDelimiters(Separators other) {
        return element == other.element && repetition == other.repetition && component == other.component
            && segment == other.segment;
    }

    /** True when ISA11 is a usable repetition separator (5010 {@code ^}); 4010 carried {@code U} there. */
    public boolean hasRepetition() {
        return !Character.isLetterOrDigit(repetition) && !Character.isWhitespace(repetition)
            && repetition != element && repetition != component && repetition != segment;
    }

    public String[] splitElements(String segmentText) {
        return segmentText.split(Pattern.quote(String.valueOf(element)), -1);
    }

    public String[] splitComponents(String elementText) {
        return elementText.split(Pattern.quote(String.valueOf(component)), -1);
    }

    public String[] splitRepetitions(String elementText) {
        return hasRepetition() ? elementText.split(Pattern.quote(String.valueOf(repetition)), -1)
            : new String[] {elementText};
    }
}
