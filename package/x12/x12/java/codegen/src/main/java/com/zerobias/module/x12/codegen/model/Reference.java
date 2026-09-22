package com.zerobias.module.x12.codegen.model;

/**
 * A {@code Property.references} value. Composition when {@code propertyName} is
 * null (embeds the referenced shape); foreign-key when present (interface
 * SchemaIds.md). X12 content is composition-only (loops, segments, composites)
 * plus enum references for coded elements, so {@code propertyName} stays null.
 */
public final class Reference {
    public String schemaId;
    public String propertyName;

    public Reference(String schemaId) {
        this.schemaId = schemaId;
    }

    public Reference(String schemaId, String propertyName) {
        this.schemaId = schemaId;
        this.propertyName = propertyName;
    }
}
