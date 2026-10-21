package com.zerobias.module.ocsf.producer;

import com.google.gson.Gson;
import com.zerobias.module.ocsf.OcsfClasses;
import com.zerobias.module.ocsf.buffer.BufferStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The receiver's operations. All methods return JSON strings (the HTTP layer passes them
 * through verbatim) and raise {@link ProducerException} for caller errors.
 *
 * <ul>
 *   <li><b>OCSF interface</b> (DESIGN §2): {@link #listClasses}, {@link #listEvents},
 *       {@link #getEvent}. Event bodies are returned exactly as the source wrote them —
 *       spliced into the response as stored text, never re-serialized.</li>
 *   <li><b>DataProducer, files only</b> (DESIGN §3): browse {@code /inbox}, {@code /files},
 *       {@code /stats} through {@link ObjectTree}. File management ({@code uploadBinaryContent},
 *       {@code createChildObject}, {@code deleteObject} under {@code /inbox}) is off unless the
 *       deployment sets {@code config.allowFileManagement=true}.</li>
 * </ul>
 */
public final class ProducerFacade {

    private static final Gson GSON = new Gson();
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 1000;

    private final BufferStore buffer;
    private final ObjectTree tree;
    private final OcsfClasses classes;
    private final boolean allowFileManagement;

    public ProducerFacade(BufferStore buffer, ObjectTree tree, OcsfClasses classes, boolean allowFileManagement) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        this.tree = Objects.requireNonNull(tree, "tree");
        this.classes = Objects.requireNonNull(classes, "classes");
        this.allowFileManagement = allowFileManagement;
    }

    /** Whether the file-management write surface is enabled ({@code isSupported} reads this). */
    public boolean fileManagementEnabled() {
        return allowFileManagement;
    }

    public OcsfClasses classes() {
        return classes;
    }

    // --- OCSF interface (DESIGN §2) --------------------------------------------

    /** {@code listClasses}: the classes with at least one stored event, as {@code OcsfClass}. */
    public String listClasses() throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int uid : buffer.distinctClassUids()) {
            classes.byUid(uid).ifPresent(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("uid", c.uid());
                m.put("name", c.name());
                m.put("caption", c.caption());
                m.put("categoryUid", c.categoryUid());
                m.put("category", c.category());
                out.add(m);
            });
        }
        return GSON.toJson(out);
    }

    /**
     * {@code list<Model>}: one page of the class's events with {@code startTime <= time < endTime},
     * newest first, in the {@code PagedResults} envelope ({@code items, count, pageSize,
     * pageNumber}) the Hub's java-http invoker expects for a paged operation.
     */
    public String listEvents(OcsfClasses.OcsfClass c, Long startTime, Long endTime, int pageSize, int pageNumber)
            throws SQLException {
        checkPaging(pageSize, pageNumber);
        if (startTime != null && endTime != null && startTime >= endTime) {
            throw ProducerException.illegalArgument("startTime must be before endTime");
        }
        List<String> bodies = buffer.eventBodies(c.uid(), startTime, endTime, pageSize, pageOffset(pageSize, pageNumber));
        long count = buffer.countEvents(c.uid(), startTime, endTime);
        return "{\"items\":[" + String.join(",", bodies) + "],\"count\":" + count
            + ",\"pageSize\":" + pageSize + ",\"pageNumber\":" + pageNumber + "}";
    }

    /** {@code get<Model>}: one event of this class by its key; 404 otherwise, including a key held by another class. */
    public String getEvent(OcsfClasses.OcsfClass c, String uid) throws SQLException {
        if (uid == null || uid.isBlank()) {
            throw ProducerException.illegalArgument("uid is required");
        }
        return buffer.eventBody(c.uid(), uid)
            .orElseThrow(() -> ProducerException.noSuchObject(c.model() + "/" + uid));
    }

    // --- DataProducer: objects ---------------------------------------------------

    public String getRootObject() throws SQLException {
        return GSON.toJson(tree.object(ObjectTree.ROOT));
    }

    public String getObject(String objectId) throws SQLException {
        requireId(objectId);
        return GSON.toJson(tree.object(objectId));
    }

    public String getChildren(String objectId, int pageSize, int pageNumber) throws SQLException {
        requireId(objectId);
        checkPaging(pageSize, pageNumber);
        ObjectTree.ChildPage paged = tree.childPage(objectId, pageSize, pageOffset(pageSize, pageNumber));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("items", paged.items());
        envelope.put("count", paged.total());
        envelope.put("pageSize", pageSize);
        envelope.put("pageNumber", pageNumber);
        return GSON.toJson(envelope);
    }

    public String getDocumentData(String objectId) throws SQLException {
        requireId(objectId);
        return GSON.toJson(tree.documentData(objectId));
    }

    /** The bytes of a {@code /files/<fileId>} or live {@code /inbox} file; served raw by the HTTP layer. */
    public BinaryContent downloadBinary(String objectId) throws SQLException {
        requireId(objectId);
        return tree.downloadBinary(objectId);
    }

    // --- DataProducer: file management, gated by config.allowFileManagement ------

    public String uploadBinary(String objectId, String fileName, byte[] bytes) throws SQLException {
        requireId(objectId);
        requireFileManagement("uploadBinaryContent");
        return GSON.toJson(tree.uploadBinary(objectId, fileName, bytes));
    }

    /** {@code createChildObject}: only containers (a directory); bytes arrive through {@code uploadBinaryContent}. */
    public String createChildObject(String objectId, String name, List<String> objectClass) throws SQLException {
        requireId(objectId);
        requireFileManagement("createChildObject");
        if (objectClass != null && !objectClass.isEmpty() && !objectClass.contains("container")) {
            throw ProducerException.illegalArgument(
                "Only container children can be created (a directory); got objectClass=" + objectClass);
        }
        return GSON.toJson(tree.createChildContainer(objectId, name));
    }

    public String deleteObject(String objectId) throws SQLException {
        requireId(objectId);
        requireFileManagement("deleteObject");
        tree.deleteObject(objectId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "deleted");
        body.put("id", objectId);
        return GSON.toJson(body);
    }

    /** 400 {@code UnsupportedOperationError} unless {@code config.allowFileManagement} is set. */
    public void requireFileManagement(String operationId) {
        if (!allowFileManagement) {
            throw ProducerException.unsupported(operationId
                + " is disabled: the receiver is receive-only unless the deployment sets "
                + "config.allowFileManagement=true");
        }
    }

    // --- helpers -------------------------------------------------------------------

    /** {@code 1 <= pageSize <= 1000}, {@code pageNumber >= 1}; out of range is a 400, never a silent default. */
    private static void checkPaging(int pageSize, int pageNumber) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ProducerException.illegalArgument("pageSize must be between 1 and " + MAX_PAGE_SIZE + ", got " + pageSize);
        }
        if (pageNumber < 1) {
            throw ProducerException.illegalArgument("pageNumber must be at least 1, got " + pageNumber);
        }
    }

    /**
     * The row offset of a 1-based page, computed exactly: int arithmetic wraps negative for a
     * large page number, and SQLite reads a negative {@code OFFSET} as 0 (page 1 again).
     */
    static int pageOffset(int pageSize, int pageNumber) {
        try {
            return Math.multiplyExact(pageNumber - 1, pageSize);
        } catch (ArithmeticException overflow) {
            throw ProducerException.illegalArgument("pageNumber " + pageNumber + " with pageSize " + pageSize
                + " is past the last addressable row");
        }
    }

    private static void requireId(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            throw ProducerException.illegalArgument("objectId is required");
        }
    }
}
