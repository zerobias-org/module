package com.zerobias.module.hl7.codegen.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A DataProducer Schema object (interface api.yml Schema): {@code id},
 * {@code dataTypes[]}, {@code properties[]} — in that wire order.
 */
public final class Schema {
    public String id;
    public List<DataType> dataTypes = new ArrayList<>();
    public List<Property> properties = new ArrayList<>();

    public Schema(String id) {
        this.id = id;
    }
}
