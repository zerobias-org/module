package com.zerobias.module.x12.codegen.model;

/**
 * One member of an enum DataType's value set. {@code description} is emitted
 * only when the source carries one (pyx12's {@code valid_codes} and
 * {@code codes.xml} ship bare codes, so it is normally absent).
 */
public final class EnumValue {
    public String value;
    public String description;

    public EnumValue(String value, String description) {
        this.value = value;
        this.description = description;
    }
}
