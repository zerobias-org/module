package com.zerobias.module.ocsf.producer;

import com.zerobias.module.ocsf.OcsfClasses;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Routes operation invocations ({@code ApiClass.methodName}, the Hub Node's java-http RPC
 * contract: {@code POST /connections/{id}/IamApi.listAuthentication} with {@code {"argMap":…}})
 * to {@link ProducerFacade}.
 *
 * <ul>
 *   <li><b>OCSF</b> — one generic branch for all class operations (DESIGN §2):
 *       {@code <Tag>Api.list<Model>} / {@code <Tag>Api.get<Model>} resolve through
 *       {@link OcsfClasses#byModel} and must carry that class's API class, so
 *       {@code SystemApi.listAuthentication} is unsupported rather than silently served.
 *       {@code ClassesApi.listClasses} lists what is held.</li>
 *   <li><b>DataProducer</b> — the files-only subset (DESIGN §3), matched by name.</li>
 * </ul>
 *
 * <p>The two binary ops carry bytes rather than JSON, so the HTTP layer serves them itself and
 * this router rejects them. File-management ops are supported only while
 * {@code config.allowFileManagement} is set, and the facade refuses them under the same flag.
 */
public final class OperationRouter {

    static final String DOWNLOAD_BINARY = "BinaryApi.downloadBinary";
    static final String UPLOAD_BINARY = "BinaryApi.uploadBinaryContent";
    static final String LIST_CLASSES = "ClassesApi.listClasses";

    /** The DataProducer operations this receiver serves. */
    private static final Set<String> PRODUCER_OPS = Set.of(
        "ObjectsApi.getRootObject",
        "ObjectsApi.getObject",
        "ObjectsApi.getChildren",
        "ObjectsApi.searchChildObjects",
        "DocumentsApi.getDocumentData",
        DOWNLOAD_BINARY);

    /** Supported only while {@code config.allowFileManagement} is set. */
    private static final Set<String> FILE_MANAGEMENT_OPS = Set.of(
        "ObjectsApi.createChildObject",
        "ObjectsApi.deleteObject",
        UPLOAD_BINARY);

    private OperationRouter() {
    }

    public static boolean isBinaryDownload(String method) {
        return DOWNLOAD_BINARY.equals(method);
    }

    /** The upload's body is raw bytes, not the JSON argMap envelope; the HTTP layer reads it. */
    public static boolean isBinaryUpload(String method) {
        return UPLOAD_BINARY.equals(method);
    }

    /**
     * {@code isSupported}: the qualified RPC name ({@code IamApi.listAuthentication}) or the bare
     * operationId ({@code listAuthentication}) of something routed and implemented here.
     */
    public static boolean isSupported(String operationId, ProducerFacade facade) {
        if (operationId == null) {
            return false;
        }
        String op = operationId.trim();
        int dot = op.indexOf('.');
        String bare = op.substring(dot + 1);
        if (dot < 0 ? bare.equals("listClasses") : op.equals(LIST_CLASSES)) {
            return true;
        }
        Optional<OcsfClasses.OcsfClass> c = classOf(facade.classes(), bare);
        if (c.isPresent()) {
            return dot < 0 || c.get().apiClass().equals(op.substring(0, dot));
        }
        return matches(PRODUCER_OPS, op, dot)
            || (facade.fileManagementEnabled() && matches(FILE_MANAGEMENT_OPS, op, dot));
    }

    private static boolean matches(Set<String> qualified, String op, int dot) {
        return dot >= 0 ? qualified.contains(op)
            : qualified.stream().anyMatch(q -> q.substring(q.indexOf('.') + 1).equals(op));
    }

    /** The class a {@code list<Model>}/{@code get<Model>} method addresses, if any. */
    private static Optional<OcsfClasses.OcsfClass> classOf(OcsfClasses classes, String methodName) {
        if (methodName.startsWith("list")) {
            return classes.byModel(methodName.substring(4));
        }
        if (methodName.startsWith("get")) {
            return classes.byModel(methodName.substring(3));
        }
        return Optional.empty();
    }

    public static String executeOperation(ProducerFacade facade, String method, Map<String, Object> argMap)
            throws Exception {
        String[] parts = method.split("\\.");
        if (parts.length != 2) {
            throw ProducerException.illegalArgument("Invalid method format: " + method + " (expected ApiClass.methodName)");
        }
        String apiClass = parts[0];
        String methodName = parts[1];
        switch (apiClass) {
            case "ObjectsApi":
                return objects(facade, methodName, argMap);
            case "DocumentsApi":
                if ("getDocumentData".equals(methodName)) {
                    return facade.getDocumentData(str(argMap, "objectId"));
                }
                throw ProducerException.unsupported("Unsupported DocumentsApi method: " + methodName);
            case "BinaryApi":
                throw ProducerException.illegalArgument(method + " carries bytes; the HTTP layer serves it, not the JSON router");
            default:
                return ocsf(facade, apiClass, methodName, argMap);
        }
    }

    private static String ocsf(ProducerFacade facade, String apiClass, String methodName, Map<String, Object> argMap)
            throws Exception {
        if (LIST_CLASSES.equals(apiClass + "." + methodName)) {
            return facade.listClasses();
        }
        OcsfClasses.OcsfClass c = classOf(facade.classes(), methodName)
            .filter(cls -> cls.apiClass().equals(apiClass))
            .orElseThrow(() -> ProducerException.unsupported("Unsupported operation: " + apiClass + "." + methodName));
        if (methodName.startsWith("get")) {
            return facade.getEvent(c, str(argMap, "uid"));
        }
        rejectSet(methodName, argMap, "pageToken");   // the interface pages by number only
        return facade.listEvents(c, longArg(argMap, "startTime"), longArg(argMap, "endTime"),
            pageSize(argMap), pageNumber(argMap));
    }

    private static String objects(ProducerFacade facade, String methodName, Map<String, Object> argMap)
            throws Exception {
        switch (methodName) {
            case "getRootObject":
                return facade.getRootObject();
            case "getObject":
                return facade.getObject(str(argMap, "objectId"));
            case "getChildren":
                rejectSet(methodName, argMap, "sortBy", "sortDir", "type", "tags", "pageToken");
                return facade.getChildren(str(argMap, "objectId"), pageSize(argMap), pageNumber(argMap));
            case "searchChildObjects":
                // No child filter/projection/sort/cursor; the count is always in the body.
                rejectSet(methodName, argMap, "sortBy", "sortDir", "filter", "pageToken", "properties");
                String scope = str(argMap, "scope");
                if (scope != null && !scope.isEmpty() && !"one_level".equals(scope)) {
                    throw ProducerException.unsupported("scope=" + scope + " is not supported by " + methodName);
                }
                requireBoolean(argMap, "includeCount");
                return facade.getChildren(str(argMap, "objectId"), pageSize(argMap), pageNumber(argMap));
            case "createChildObject": {
                facade.requireFileManagement(methodName);   // disabled = unsupported, whatever the args
                Map<String, Object> request = createObjectRequest(argMap);
                for (String field : request.keySet()) {
                    if (!Set.of("id", "name", "objectClass").contains(field) && isSet(request.get(field))) {
                        throw ProducerException.unsupported(field + " is not supported by " + methodName);
                    }
                }
                String name = str(request, "name");
                return facade.createChildObject(str(argMap, "objectId"), name != null ? name : str(request, "id"),
                    childClasses(request));
            }
            case "deleteObject":
                facade.requireFileManagement(methodName);
                requireBoolean(argMap, "recursive");
                if (Boolean.TRUE.equals(argMap.get("recursive")) || "true".equals(argMap.get("recursive"))) {
                    throw ProducerException.unsupported("recursive=true is not supported: delete a directory's children first");
                }
                return facade.deleteObject(str(argMap, "objectId"));
            default:
                throw ProducerException.unsupported("Unsupported ObjectsApi method: " + methodName);
        }
    }

    // --- createChildObject args ------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> createObjectRequest(Map<String, Object> argMap) {
        if (!(argMap.get("createObjectRequest") instanceof Map<?, ?> m)) {
            throw ProducerException.illegalArgument("createObjectRequest is required and must be an object");
        }
        return (Map<String, Object>) m;
    }

    private static List<String> childClasses(Map<String, Object> request) {
        Object classes = request.get("objectClass");
        if (classes == null) {
            return List.of();
        }
        if (!(classes instanceof List<?> list)) {
            throw ProducerException.illegalArgument("objectClass must be an array");
        }
        List<String> out = new ArrayList<>();
        for (Object c : list) {
            out.add(String.valueOf(c));
        }
        return out;
    }

    // --- arg coercion ----------------------------------------------------------

    /** 400 {@code UnsupportedOperationError} when any of {@code params} is set (empty = unset). */
    private static void rejectSet(String method, Map<String, Object> argMap, String... params) {
        for (String p : params) {
            if (isSet(argMap.get(p))) {
                throw ProducerException.unsupported(p + " is not supported by " + method + " on this producer");
            }
        }
    }

    private static boolean isSet(Object v) {
        if (v instanceof String s) {
            return !s.isEmpty();
        }
        if (v instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        return v != null;
    }

    private static int pageNumber(Map<String, Object> argMap) {
        Long n = longArg(argMap, "pageNumber");
        return n == null ? 1 : toInt("pageNumber", n);
    }

    private static int pageSize(Map<String, Object> argMap) {
        Long n = longArg(argMap, "pageSize");
        return n == null ? ProducerFacade.DEFAULT_PAGE_SIZE : toInt("pageSize", n);
    }

    private static int toInt(String key, long v) {
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw ProducerException.illegalArgument(key + " is out of range: " + v);
        }
        return (int) v;
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * An integer argument: a whole JSON number or its decimal string; unset → null; anything else
     * is a 400. Gson reads every JSON number as a double, exact up to 2^53 — far past any epoch-ms.
     */
    private static Long longArg(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null || (v instanceof String s && s.isBlank())) {
            return null;
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) <= (1L << 53)) {
                return (long) d;
            }
        } else if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                // fall through to the 400
            }
        }
        throw ProducerException.illegalArgument(key + " must be an integer, got " + v);
    }

    /** 400 unless {@code key} is unset, a boolean, or {@code "true"}/{@code "false"}. */
    private static void requireBoolean(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v != null && !(v instanceof Boolean) && !"true".equals(v) && !"false".equals(v)) {
            throw ProducerException.illegalArgument(key + " must be a boolean, got " + v);
        }
    }
}
