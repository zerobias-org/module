package com.zerobias.module.hl7.materializer;

/**
 * Decides which structure a message materializes against (DESIGN §7.2). The base
 * structure is the MSH-9-3-derived name; an extension discriminator may override
 * it (e.g. an EPIC-sent ADT → {@code ADT_A01_with_ZPV}). Kept as a tiny interface
 * so the materializer stays decoupled from the {@code ext} package — the boot
 * layer supplies an implementation built from the loaded discriminators.
 */
@FunctionalInterface
public interface StructureResolver {

    /** No extensions: always the default (base) structure. */
    StructureResolver DEFAULT = (messageCode, sendingApp, defaultStructure) -> defaultStructure;

    /**
     * @param messageCode      MSH-9-1 (e.g. {@code ADT})
     * @param sendingApp       MSH-3 (e.g. {@code EPIC}); may be null
     * @param defaultStructure the base structure name (e.g. {@code ADT_A01}); may be null
     * @return the structure name to materialize against
     */
    String resolve(String messageCode, String sendingApp, String defaultStructure);
}
