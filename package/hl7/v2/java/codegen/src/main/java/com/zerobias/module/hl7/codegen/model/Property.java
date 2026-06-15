package com.zerobias.module.hl7.codegen.model;

/**
 * A DataProducer {@code Schema.properties[]} entry (interface api.yml Property).
 * Field order is the wire order. {@code required} is always emitted; {@code multi}
 * and {@code primaryKey} use boxed Boolean so they are omitted when not true
 * (matching the SQL module's emitter); {@code references}/{@code format} omitted
 * when null.
 */
public final class Property {
    public String name;
    public String dataType;
    public boolean required;
    public Boolean multi;
    public Boolean primaryKey;
    public String format;
    public Reference references;

    public Property(String name, String dataType) {
        this.name = name;
        this.dataType = dataType;
    }

    public Property required(boolean v) {
        this.required = v;
        return this;
    }

    public Property multi(boolean v) {
        this.multi = v ? Boolean.TRUE : null;
        return this;
    }

    public Property primaryKey(boolean v) {
        this.primaryKey = v ? Boolean.TRUE : null;
        return this;
    }

    public Property format(String v) {
        this.format = v;
        return this;
    }

    public Property references(Reference v) {
        this.references = v;
        return this;
    }
}
