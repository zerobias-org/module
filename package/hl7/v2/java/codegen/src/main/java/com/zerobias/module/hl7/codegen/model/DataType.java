package com.zerobias.module.hl7.codegen.model;

import java.util.List;

/**
 * A core DataType entity as it appears in a schema's {@code dataTypes[]} array
 * (interface {@code @zerobias-org/types-core/schema/type.yml}). Field order is
 * the wire order; null fields are omitted by Gson.
 */
public final class DataType {
    public String name;
    public String jsonType;
    public String description;
    public List<Object> examples;
    public String htmlInput;
    public boolean isEnum;

    public DataType(String name, String jsonType, String description, List<Object> examples, String htmlInput) {
        this.name = name;
        this.jsonType = jsonType;
        this.description = description;
        this.examples = examples;
        this.htmlInput = htmlInput;
        this.isEnum = false;
    }
}
