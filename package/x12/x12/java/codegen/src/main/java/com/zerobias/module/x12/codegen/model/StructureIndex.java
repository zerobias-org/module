package com.zerobias.module.x12.codegen.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The runtime materializer driver for one implementation guide (DESIGN §5/§6),
 * emitted as {@code structure-index/<GS08>.json}. Plain data: the receiver
 * copies this class (and only this class) into its materializer package and
 * loads the JSON with Gson at boot.
 *
 * <p>For every loop it records, in map order, the property name / xid / kind /
 * cardinality of each child (segment or nested loop); for every segment and
 * composite it records the positional field layout — property name, data
 * element number, X12 type, core type, implied decimals for {@code N<n>},
 * min/max length, cardinality, and the composite or code-set key when the
 * field is one. Loop entries are keyed by loop xid (the ISA/GS/ST envelope
 * loops included, {@link #transactionLoop} names the atom root); segment
 * entries by segment xid; composite entries by composite data element
 * ({@code C003}). Where a guide uses the same xid in several places the entry
 * is the merged union of all uses, exactly like the emitted schema.
 *
 * <p>Insertion-ordered maps so the JSON is stable and reviewable.
 */
public final class StructureIndex {
    public String gs08;
    /** Display transaction type ({@code 835}, {@code 837P}, ...); DESIGN §2.1. */
    public String transactionType;
    /** The map's {@code <transaction xid>} ({@code 835W1}, {@code 837}, ...). */
    public String transactionXid;
    /** Classpath resource the index was generated from ({@code mapping/835.5010.X221.A1.xml}). */
    public String mapFile;
    /** The canonical GS08 when this index is an alias copy (e.g. 005010X223A1 -> 005010X223A2). */
    public String aliasOf;
    /** Loop xid whose subtree is the transaction-set atom (ST..SE). */
    public String transactionLoop;
    /** {@code schema:table:x12.<GS08>.<TS>} — the collection schema of this guide's atoms. */
    public String tableSchemaId;
    public Map<String, LoopEntry> loops = new LinkedHashMap<>();
    public Map<String, SegmentEntry> segments = new LinkedHashMap<>();
    public Map<String, CompositeEntry> composites = new LinkedHashMap<>();

    public StructureIndex() {
    }

    /** Ordered child layout of a loop. */
    public static final class LoopEntry {
        public String xid;
        public String name;
        /** {@code schema:type:x12.<GS08>.<xid>}; the {@code schema:table:} id for the transaction loop (ST_LOOP); null for the envelope loops (ISA/GS). */
        public String schemaId;
        public List<StructureRef> structures = new ArrayList<>();
    }

    /** One child of a loop: a segment or a nested loop. */
    public static final class StructureRef {
        /** Property name in the materialized JSON ({@code clp}, {@code loop2100}). */
        public String name;
        public String xid;
        /** {@code "segment"} or {@code "loop"}. */
        public String kind;
        public boolean required;
        public boolean multi;
        /** Map position ({@code 0100}); first occurrence when merged. */
        public String pos;
        /** {@code repeat} (loop) / {@code max_use} (segment) from the map: {@code 1}, {@code 10}, {@code >1}. */
        public String repeat;

        public StructureRef(String name, String xid, String kind, boolean required, boolean multi, String pos, String repeat) {
            this.name = name;
            this.xid = xid;
            this.kind = kind;
            this.required = required;
            this.multi = multi;
            this.pos = pos;
            this.repeat = repeat;
        }
    }

    /** Ordered positional field layout of a segment. */
    public static final class SegmentEntry {
        public String xid;
        public String name;
        public String schemaId;
        public List<FieldEntry> fields = new ArrayList<>();
    }

    /** Ordered positional sub-element layout of a composite data element. */
    public static final class CompositeEntry {
        public String dataEle;
        public String name;
        public String schemaId;
        public List<FieldEntry> fields = new ArrayList<>();
    }

    /** One element (or composite slot) of a segment / composite. */
    public static final class FieldEntry {
        /** 1-based element position within the segment/composite. */
        public int seq;
        /** Property name in the materialized JSON ({@code clp02}, {@code c00301}). */
        public String name;
        /** Map xid of the first occurrence ({@code CLP02}, {@code SVC01-01}). */
        public String xid;
        /** Data element number ({@code 1029}, {@code I01}) or composite id ({@code C003}). */
        public String dataEle;
        /** pyx12 data type: {@code AN ID N0..N9 R DT TM B}; null for a composite slot. */
        public String x12Type;
        /** Core dataType ({@code string decimal date byte}); null for a composite slot. */
        public String coreType;
        /** For {@code N<n>}: the implied decimal places; else null. */
        public Integer impliedDecimals;
        public Integer minLen;
        public Integer maxLen;
        public boolean required;
        public boolean multi;
        /** Element-level {@code repeat} (the {@code ^} repetition separator), else null. */
        public Integer repeat;
        /** Composite data element ({@code C003}) when this slot is a composite; else null. */
        public String composite;
        /** Data element whose {@code schema:enum:x12.codes.<dataEle>} applies; else null. */
        public String codes;
        /** Merged IG usage: {@code R} when required in every use, else {@code S}; {@code N} when never used. */
        public String usage;
    }
}
