package com.zerobias.module.hl7.ext;

/**
 * A rule that routes an inbound message to an extension's augmented structure
 * (DESIGN §7.2): "if MSH-3 == 'EPIC' and the message code is ADT, use
 * {@code epic.ADT_A01_with_ZPV}". MSH-3 (sendingApplication) is the discriminator
 * key (§11.4 default).
 *
 * <p>Used two ways: the materializer applies {@link #matches} to pick the
 * structure to walk; the object tree uses {@link #whereClause} to scope the
 * extension's {@code /by-type/<structure>} collection over the buffer.
 *
 * @param namespace    the extension's schema namespace (e.g. {@code epic})
 * @param messageCode  MSH-9-1 to match (e.g. {@code ADT})
 * @param senderEquals MSH-3 value to match (e.g. {@code EPIC})
 * @param structure    the augmented structure name (e.g. {@code ADT_A01_with_ZPV})
 */
public record Discriminator(String namespace, String messageCode, String senderEquals, String structure) {

    /** The full collection schema id this structure resolves to. */
    public String schemaId() {
        return "schema:table:hl7v2." + namespace + "." + structure;
    }

    public boolean matches(String code, String sendingApp) {
        return messageCode.equals(code) && senderEquals.equals(sendingApp);
    }

    /** SQLite WHERE fragment selecting the rows this extension structure covers. */
    public String whereClause() {
        return "message_code = " + sql(messageCode) + " AND sending_app = " + sql(senderEquals);
    }

    private static String sql(String v) {
        return "'" + v.replace("'", "''") + "'";
    }
}
