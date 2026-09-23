package com.zerobias.module.x12.producer;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * <b>To be implemented by {@code ObjectTree}</b> (producer phase, DESIGN §12 step 4):
 * the DataProducer object hierarchy of DESIGN §2.1 —
 *
 * <pre>
 * /                              container (root; id == name == "/")
 * └─ /x12-receiver               container
 *    ├─ /files                   container → /files/&lt;fileId&gt; ["container","binary"]
 *    │                                       → /files/&lt;fileId&gt;/transactions  collection (envelope)
 *    ├─ /transactions            collection (all rows, envelope schema)
 *    ├─ /by-type                 container → /by-type/&lt;TS&gt; [ /&lt;GS08&gt; when a TS spans versions ]
 *    ├─ /by-version              container → /by-version/&lt;GS08&gt;
 *    ├─ /by-sender               container → /by-sender/&lt;ISA06&gt;
 *    ├─ /by-source               container → /by-source/&lt;sourceName&gt;
 *    ├─ /stats                   document (schema:shared:x12.receiver-stats)
 *    └─ /ops                     container → /ops/&lt;fn&gt; functions (DESIGN §2.5)
 * </pre>
 *
 * Object metadata maps use the hl7/v2 shapes: {@code {id, name, objectClass:[...]}} plus
 * {@code collectionSchema}/{@code collectionSize} for collections, {@code documentSchema}
 * for documents, {@code inputSchema}/{@code outputSchema}/{@code throws} for functions,
 * and the DESIGN §2.1 binary fields ({@code fileName, size, mimeType, checksum, modified,
 * created, tags}) for {@code /files/<fileId>}.
 *
 * <p>The foundation ships only {@link #ROOT_ONLY}: the mandatory root container, no
 * children, everything else {@code noSuchObjectError}.
 */
public interface ObjectTreeApi {

    String ROOT = "/";
    String RECEIVER = "/x12-receiver";

    /**
     * A collection's buffer scope (a WHERE fragment over {@code transactions}; null = all
     * rows) + its element schema id (DESIGN §2.1 homogeneity rule).
     */
    record Collection(String id, String scopeWhere, String schemaId) {
    }

    /** Object metadata for {@code id}, or throw {@code noSuchObjectError}. (§2.1) */
    Map<String, Object> object(String id) throws SQLException;

    /** Direct children of {@code id} (emergent from the buffer's DISTINCT values), or throw {@code noSuchObjectError}. (§2.1) */
    List<Map<String, Object>> children(String id) throws SQLException;

    /**
     * Resolve a collection id to its buffer scope, or throw: {@code noSuchObjectError} for
     * an unknown discriminator value, {@code UnsupportedOperationError} if the id is a
     * container rather than a collection. (§2.1, §2.6)
     */
    Collection resolveCollection(String id) throws SQLException;

    /**
     * The document body for a document node ({@code /stats}: poller + buffer metrics,
     * DESIGN §2.1 / §4.2 backpressure / §11.2 {@code .done} count+age), or throw
     * {@code noSuchObjectError} / {@code UnsupportedOperationError} for non-documents.
     */
    Map<String, Object> documentData(String id) throws SQLException;

    /**
     * The bytes of a {@code /files/<fileId>} node, resolved through
     * {@code files.current_path} so download works after the {@code .done} rename
     * (DESIGN §2.8). Throws {@code noSuchObjectError} for an unknown file and
     * {@link ProducerException#fileGone} when the bytes were removed by inbox hygiene.
     */
    BinaryContent downloadBinary(String id) throws SQLException;

    /**
     * Write bytes as {@code fileName} into the container {@code id} (DESIGN §2.9,
     * {@code uploadBinaryContent}). Only the live {@code /inbox} branch accepts this; the
     * default and the {@code /files} projection reject it, since {@code /files} is a view
     * of the buffer rather than of the volume.
     */
    default Map<String, Object> uploadBinary(String id, String fileName, byte[] bytes) throws SQLException {
        throw ProducerException.unsupported("Files are receive-only; drop them in the inbox, not via upload");
    }

    /**
     * Create a child container (mkdir) named {@code name} under {@code id} (DESIGN §2.9,
     * {@code createChildObject}). Only the live {@code /inbox} branch accepts this.
     */
    default Map<String, Object> createChildContainer(String id, String name) throws SQLException {
        throw ProducerException.unsupported("Object tree is fixed (receive-only): createChildObject");
    }

    /**
     * Delete the object at {@code id} (DESIGN §2.9, {@code deleteObject}). Only the live
     * {@code /inbox} branch accepts this: a file is unlinked, an empty directory removed.
     * The emergent branches cannot be deleted — they are projections of the buffer, and
     * {@code ops/purge} is how buffer rows leave.
     */
    default void deleteObject(String id) throws SQLException {
        throw ProducerException.unsupported("Object tree is fixed (receive-only): deleteObject");
    }

    /** Root container only; every other id is unknown. */
    ObjectTreeApi ROOT_ONLY = new ObjectTreeApi() {
        @Override
        public Map<String, Object> object(String id) {
            if (ROOT.equals(id)) {
                Map<String, Object> o = new java.util.LinkedHashMap<>();
                o.put("id", ROOT);
                o.put("name", ROOT);
                o.put("objectClass", List.of("container"));
                return o;
            }
            throw ProducerException.noSuchObject(id);
        }

        @Override
        public List<Map<String, Object>> children(String id) {
            object(id);
            return List.of();
        }

        @Override
        public Collection resolveCollection(String id) {
            object(id);
            throw ProducerException.unsupported("Object is not a collection: " + id);
        }

        @Override
        public Map<String, Object> documentData(String id) {
            object(id);
            throw ProducerException.unsupported("Object is not a document: " + id);
        }

        @Override
        public BinaryContent downloadBinary(String id) {
            object(id);
            throw ProducerException.unsupported("Object is not a binary: " + id);
        }
    };
}
