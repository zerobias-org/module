package com.zerobias.module.x12.producer;

/**
 * <b>To be implemented by {@code SchemaRegistry}</b> (producer phase, DESIGN §12 step 2):
 * the catalog of schema JSON generated at build time into
 * {@code classpath:schemas/**} (DESIGN §6), addressed by the ids in DESIGN §2.2
 * ({@code schema:table:x12.<GS08>.<TS>}, {@code schema:type:x12.<GS08>.<xid>},
 * {@code schema:enum:x12.codes.<dataEle>}, {@code schema:shared:x12.*},
 * {@code schema:function:x12.ops.<fn>:input|output}).
 *
 * <p>The foundation ships only {@link #EMPTY} (every lookup 404s).
 */
public interface SchemaRegistryApi {

    /** Raw schema JSON for {@code schemaId}, or throw {@link ProducerException#noSuchSchema}. (§2.2) */
    String getSchema(String schemaId);

    /** Whether {@code schemaId} is registered. (§2.2) */
    boolean has(String schemaId);

    /** Number of registered schemas (health/boot log). */
    int size();

    /** No schemas loaded: every lookup is {@code noSuchObjectError}. */
    SchemaRegistryApi EMPTY = new SchemaRegistryApi() {
        @Override
        public String getSchema(String schemaId) {
            throw ProducerException.noSuchSchema(schemaId);
        }

        @Override
        public boolean has(String schemaId) {
            return false;
        }

        @Override
        public int size() {
            return 0;
        }
    };
}
