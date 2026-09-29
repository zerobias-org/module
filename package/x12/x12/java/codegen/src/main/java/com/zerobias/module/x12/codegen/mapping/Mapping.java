package com.zerobias.module.x12.codegen.mapping;

import java.util.List;

/**
 * In-memory form of the pyx12 mapping files imsweb ships ({@code mapping/*.xml},
 * schema {@code map.xsd} / {@code map.v2.xsd}). Pure data; the
 * {@link MappingLoader} builds it, the walker consumes it.
 */
public final class Mapping {

    private Mapping() {
    }

    /** {@code <transaction xid>}: one implementation-guide map. */
    public record Transaction(String xid, String name, Loop root) {
    }

    /** A loop child: {@link Loop} or {@link Segment}, in map order. */
    public sealed interface Structure permits Loop, Segment {
        String xid();

        String name();

        String usage();

        String pos();
    }

    /** {@code <loop>}: {@code repeat} is {@code 1}, {@code 10}, {@code >1}, ... */
    public record Loop(String xid, String name, String usage, String pos, String repeat, String type,
                       List<Structure> children) implements Structure {
    }

    /** {@code <segment>}: {@code maxUse} is {@code 1}, {@code 10}, {@code >1}, ... */
    public record Segment(String xid, String name, String usage, String pos, String maxUse, String endTag,
                          List<String> syntax, List<Field> fields) implements Structure {
    }

    /** A segment (or composite) slot: {@link Element} or {@link Composite}. */
    public sealed interface Field permits Element, Composite {
        String xid();

        String dataEle();

        String name();

        String usage();

        int seq();

        Integer repeat();
    }

    /**
     * {@code <element>}. {@code validCodes} are the inline {@code <valid_codes><code>}
     * values; {@code externalCodes} the {@code valid_codes/@external} code-set id
     * (a {@code codes.xml} codeset); either may be empty/null.
     */
    public record Element(String xid, String dataEle, String name, String usage, int seq, Integer repeat,
                          List<String> validCodes, String externalCodes, String regex) implements Field {
    }

    /** {@code <composite>}: {@code dataEle} is the composite id ({@code C003}). */
    public record Composite(String xid, String dataEle, String name, String usage, int seq, Integer repeat,
                            List<Element> elements) implements Field {
    }

    /** {@code dataele.xml} row: {@code type} is {@code AN ID N0..N9 R DT TM B}. */
    public record DataElement(String num, String type, Integer minLen, Integer maxLen, String name) {
    }

    /** {@code codes.xml} codeset: {@code dataEle} may be null (entity_id, service_type). */
    public record CodeSet(String id, String name, String dataEle, List<String> codes) {
    }
}
