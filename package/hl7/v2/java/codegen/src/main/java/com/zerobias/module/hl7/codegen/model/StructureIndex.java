package com.zerobias.module.hl7.codegen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The runtime materializer driver (DESIGN §5). For each generated HL7 version it
 * records, in HL7 positional order, how to walk a generic-parsed message into
 * typed JSON: the segment/group layout of each message, the field layout of each
 * segment (name, type, multi, required, bound table), and the component layout of
 * each composite datatype. Emitted as {@code structure-index/<version>.json} and
 * loaded by the materializer at boot (merged with extension entries, DESIGN §7).
 *
 * <p>Insertion-ordered maps so the JSON is stable and reviewable.
 */
public final class StructureIndex {
    public String version;
    public Map<String, MessageEntry> messages = new LinkedHashMap<>();
    public Map<String, MessageEntry> groups = new LinkedHashMap<>();
    public Map<String, SegmentEntry> segments = new LinkedHashMap<>();
    public Map<String, DatatypeEntry> datatypes = new LinkedHashMap<>();

    public StructureIndex(String version) {
        this.version = version;
    }

    /**
     * Ordered segment/group layout of a message or nested group structure (both
     * are HAPI Groups). Messages live in {@link #messages}, nested groups in
     * {@link #groups}; the materializer recurses across both maps.
     */
    public static final class MessageEntry {
        public List<StructureRef> structures = new ArrayList<>();
    }

    /** One child of a message/group: a segment or a nested group. */
    public static final class StructureRef {
        public String name;        // property name (lowercased structure code, e.g. "pid")
        public String structure;   // structure simple name (e.g. "PID", "ADT_A01_INSURANCE")
        public boolean group;      // true if a nested group, false if a segment
        public boolean required;
        public boolean multi;

        public StructureRef(String name, String structure, boolean group, boolean required, boolean multi) {
            this.name = name;
            this.structure = structure;
            this.group = group;
            this.required = required;
            this.multi = multi;
        }
    }

    /** Ordered field layout of a segment. */
    public static final class SegmentEntry {
        public List<FieldEntry> fields = new ArrayList<>();
    }

    public static final class FieldEntry {
        public int field;          // 1-based HL7 field number
        public String name;        // bean property name (e.g. "patientIdentifierList")
        public String type;        // HL7 datatype simple name (e.g. "CX", "ST")
        public boolean multi;
        public boolean required;
        public Integer table;      // bound HL7 table number for ID/IS fields, else null

        public FieldEntry(int field, String name, String type, boolean multi, boolean required, Integer table) {
            this.field = field;
            this.name = name;
            this.type = type;
            this.multi = multi;
            this.required = required;
            this.table = table;
        }
    }

    /** Ordered component layout of a composite datatype. */
    public static final class DatatypeEntry {
        public List<ComponentEntry> components = new ArrayList<>();
    }

    public static final class ComponentEntry {
        public int component;      // 1-based HL7 component number
        public String name;        // bean property name (e.g. "assigningAuthority")
        public String type;        // HL7 datatype simple name (e.g. "HD", "ST")
        public Integer table;      // bound HL7 table for ID/IS components, else null

        public ComponentEntry(int component, String name, String type, Integer table) {
            this.component = component;
            this.name = name;
            this.type = type;
            this.table = table;
        }
    }
}
