package com.zerobias.module.hl7.codegen.model;

/**
 * A {@code Property.references} value. Composition when {@code propertyName} is
 * null (embeds the referenced shape); foreign-key when present (DESIGN §2.3,
 * interface SchemaIds.md). HL7 content is composition-only, so {@code propertyName}
 * stays null here.
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
