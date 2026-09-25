package com.zerobias.module.x12.producer;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Routes DataProducer operation invocations ({@code ApiClass.methodName}, e.g.
 * {@code ObjectsApi.getRootObject}) to {@link X12ProducerFacade} methods — the same
 * dispatch contract the SQL generic module and hl7/v2 use, so the Hub Node's
 * java-http invoker drives this module unchanged. {@code ApiClass} is the interface tag
 * ({@code objects} → {@code ObjectsApi}, …) and {@code methodName} its operationId, matched
 * exactly; the {@code argMap} keys are the operation's parameter names as the interface's
 * generated client declares them ({@code schemaId}, {@code createObjectRequest},
 * {@code requestBody}, {@code validateFunctionInputRequest}). There are no aliases: a
 * second spelling is a second contract nobody tests.
 *
 * <p>The two binary ops are the exceptions, because their bodies are bytes rather than
 * JSON: {@code BinaryApi.downloadBinary} ({@link #isBinaryDownload}) returns bytes and
 * {@code BinaryApi.uploadBinaryContent} ({@link #isBinaryUpload}) receives them, so the HTTP
 * layer serves both itself ({@link X12ProducerFacade#downloadBinary} /
 * {@link X12ProducerFacade#uploadBinary}) and this router rejects them.
 *
 * <p>{@link #isSupported} is an explicit whitelist of what is routed and implemented; the
 * file-management ops ({@code createChildObject}, {@code deleteObject},
 * {@code uploadBinaryContent}) are in it only while {@code config.allowFileManagement} is set
 * (DESIGN §2.9), and the facade refuses them under the same flag.
 */
public final class OperationRouter {

    private static final Gson GSON = new Gson();

    static final String DOWNLOAD_BINARY = "BinaryApi.downloadBinary";
    static final String UPLOAD_BINARY = "BinaryApi.uploadBinaryContent";

    /** The operations this producer serves; every other operation id is unsupported. */
    private static final Set<String> SUPPORTED = Set.of(
        "ObjectsApi.getRootObject",
        "ObjectsApi.getObject",
        "ObjectsApi.getChildren",
        "ObjectsApi.searchChildObjects",
        "CollectionsApi.getCollectionElements",
        "CollectionsApi.searchCollectionElements",
        "CollectionsApi.getCollectionElement",
        "SchemasApi.getSchema",
        "DocumentsApi.getDocumentData",
        "FunctionsApi.invokeFunction",
        "FunctionsApi.validateFunctionInput",
        DOWNLOAD_BINARY);

    /** Supported only while {@code config.allowFileManagement} is set (DESIGN §2.9). */
    private static final Set<String> FILE_MANAGEMENT = Set.of(
        "ObjectsApi.createChildObject",
        "ObjectsApi.deleteObject",
        UPLOAD_BINARY);

    private static final Set<String> SUPPORTED_OPERATION_IDS = operationIds(SUPPORTED);
    private static final Set<String> FILE_MANAGEMENT_OPERATION_IDS = operationIds(FILE_MANAGEMENT);

    private OperationRouter() {
    }

    /** Whether {@code method} is the binary download, which the HTTP layer serves itself. */
    public static boolean isBinaryDownload(String method) {
        return DOWNLOAD_BINARY.equals(method);
    }

    /**
     * Whether {@code method} is the binary upload ({@code BinaryApi.uploadBinaryContent}). Its
     * request body is raw bytes rather than the JSON {@code argMap} envelope, so the HTTP
     * layer must read the body itself — {@link #executeOperation} rejects it, symmetrically
     * with download.
     */
    public static boolean isBinaryUpload(String method) {
        return UPLOAD_BINARY.equals(method);
    }

    /**
     * {@code isSupported}: true for the operations routed and implemented here, and for the
     * file-management ops only when {@code fileManagement} ({@code config.allowFileManagement})
     * is set. Accepts the bare OpenAPI operationId ({@code getChildren}) or the qualified RPC
     * name ({@code ObjectsApi.getChildren}); data writes, {@code objectSearch} and anything
     * unrouted are false.
     */
    public static boolean isSupported(String operationId, boolean fileManagement) {
        if (operationId == null) {
            return false;
        }
        String op = operationId.trim();
        if (SUPPORTED.contains(op) || SUPPORTED_OPERATION_IDS.contains(op)) {
            return true;
        }
        return fileManagement && (FILE_MANAGEMENT.contains(op) || FILE_MANAGEMENT_OPERATION_IDS.contains(op));
    }

    private static Set<String> operationIds(Set<String> qualified) {
        Set<String> out = new LinkedHashSet<>();
        for (String q : qualified) {
            out.add(q.substring(q.indexOf('.') + 1));
        }
        return Set.copyOf(out);
    }

    public static String executeOperation(X12ProducerFacade facade, String method,
            Map<String, Object> argMap) throws Exception {
        String[] parts = method.split("\\.");
        if (parts.length != 2) {
            throw ProducerException.illegalArgument(
                "Invalid method format: " + method + " (expected ApiClass.methodName)");
        }
        String apiClass = parts[0];
        String methodName = parts[1];

        switch (apiClass) {
            case "ObjectsApi":
                return objects(facade, methodName, argMap);
            case "CollectionsApi":
                return collections(facade, methodName, argMap);
            case "SchemasApi":
                return schemas(facade, methodName, argMap);
            case "FunctionsApi":
                return functions(facade, methodName, argMap);
            case "DocumentsApi":
                return documents(facade, methodName, argMap);
            case "BinaryApi":
                return binary(methodName);
            default:
                throw ProducerException.unsupported("Unknown API class: " + apiClass);
        }
    }

    private static String objects(X12ProducerFacade facade, String methodName,
            Map<String, Object> argMap) throws Exception {
        switch (methodName) {
            case "getRootObject":
                return facade.getRootObject();
            case "getObject":
                return facade.getObject(str(argMap, "objectId"));
            case "getChildren":
                // A child listing has one fixed order and no type/tag facets or cursors.
                rejectSet(methodName, argMap, "sortBy", "sortDir", "type", "tags", "pageToken");
                return facade.getChildren(str(argMap, "objectId"), pageSize(argMap), pageNumber(argMap));
            case "searchChildObjects":
                // No child filter/projection/sort/cursor; the count is always in the body, so
                // includeCount holds whichever way it is set.
                rejectSet(methodName, argMap, "sortBy", "sortDir", "filter", "pageToken", "properties");
                requireOneLevelScope(methodName, argMap);
                requireBoolean(argMap, "includeCount");
                return facade.getChildren(str(argMap, "objectId"), pageSize(argMap), pageNumber(argMap));
            case "createChildObject": {
                facade.requireFileManagement(methodName);   // disabled = unsupported, whatever the args
                Map<String, Object> request = createObjectRequest(argMap);
                rejectSet(methodName, request, unhonoured(request.keySet()));
                String name = str(request, "name");
                return facade.createChildObject(str(argMap, "objectId"), name != null ? name : str(request, "id"),
                    childClasses(request));
            }
            case "deleteObject":
                facade.requireFileManagement(methodName);
                requireBoolean(argMap, "recursive");
                if (Boolean.TRUE.equals(argMap.get("recursive")) || "true".equals(argMap.get("recursive"))) {
                    throw ProducerException.unsupported("recursive=true is not supported by " + methodName
                        + " on this producer: delete a directory's children first");
                }
                return facade.deleteObject(str(argMap, "objectId"));
            case "updateObject":
                throw ProducerException.unsupported(
                    "Object tree is fixed (receive-only): " + methodName);
            default:
                throw ProducerException.unsupported("Unsupported ObjectsApi method: " + methodName);
        }
    }

    private static String collections(X12ProducerFacade facade, String methodName,
            Map<String, Object> argMap) throws Exception {
        switch (methodName) {
            case "getCollectionElements":
            case "searchCollectionElements":
                // sortBy/sortDir are honoured (SQL ORDER BY / typed comparator); cursors and
                // property projection are not implemented, so setting them is a 400.
                // getCollectionElements honours filter like searchCollectionElements does
                // (the hl7/v2 contract callers already use).
                rejectSet(methodName, argMap, "pageToken", "properties");
                return facade.getCollectionElements(
                    str(argMap, "objectId"),
                    str(argMap, "filter"),
                    sortArg(argMap, "sortBy"),
                    sortArg(argMap, "sortDir"),
                    pageSize(argMap),
                    pageNumber(argMap),
                    null);
            case "getCollectionElement":
                return facade.getCollectionElement(
                    str(argMap, "objectId"), str(argMap, "elementKey"));
            case "addCollectionElement":
                return facade.createCollectionElement(str(argMap, "objectId"), null);
            case "updateCollectionElement":
                return facade.updateCollectionElement(str(argMap, "objectId"), str(argMap, "elementKey"), null);
            case "deleteCollectionElement":
                facade.deleteCollectionElement(str(argMap, "objectId"), str(argMap, "elementKey"));
                return "{\"status\":\"deleted\"}";
            default:
                throw ProducerException.unsupported("Unsupported CollectionsApi method: " + methodName);
        }
    }

    private static String schemas(X12ProducerFacade facade, String methodName,
            Map<String, Object> argMap) {
        if ("getSchema".equals(methodName)) {
            return facade.getSchema(str(argMap, "schemaId"));
        }
        throw ProducerException.unsupported("Unsupported SchemasApi method: " + methodName);
    }

    private static String functions(X12ProducerFacade facade, String methodName,
            Map<String, Object> argMap) throws Exception {
        switch (methodName) {
            case "invokeFunction":
                return facade.invokeFunction(str(argMap, "objectId"), body(argMap.get("requestBody")));
            case "validateFunctionInput":
                return facade.validateFunctionInput(str(argMap, "objectId"),
                    body(argMap.get("validateFunctionInputRequest")));
            default:
                throw ProducerException.unsupported("Unsupported FunctionsApi method: " + methodName);
        }
    }

    private static String documents(X12ProducerFacade facade, String methodName,
            Map<String, Object> argMap) throws Exception {
        switch (methodName) {
            case "getDocumentData":
                return facade.getDocumentData(str(argMap, "objectId"));
            case "updateDocumentData":
                throw ProducerException.unsupported("Documents are read-only: " + methodName);
            default:
                throw ProducerException.unsupported("Unsupported DocumentsApi method: " + methodName);
        }
    }

    private static String binary(String methodName) {
        if ("downloadBinary".equals(methodName)) {
            throw ProducerException.illegalArgument(
                DOWNLOAD_BINARY + " streams bytes; the HTTP layer serves it, not the JSON router");
        }
        if ("uploadBinaryContent".equals(methodName)) {
            throw ProducerException.illegalArgument(
                UPLOAD_BINARY + " carries raw bytes; the HTTP layer serves it, not the JSON router");
        }
        throw ProducerException.unsupported("Unsupported BinaryApi method: " + methodName);
    }

    // --- createChildObject args (CreateObjectRequest) ----------------------

    /**
     * The {@code createObjectRequest} body. The new child's name is its {@code name}, or its
     * {@code id} (the interface makes {@code id} optional and generated, and callers send one
     * or the other).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> createObjectRequest(Map<String, Object> argMap) {
        Object v = argMap.get("createObjectRequest");
        if (!(v instanceof Map)) {
            throw ProducerException.illegalArgument("createObjectRequest is required and must be an object");
        }
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    private static List<String> childClasses(Map<String, Object> request) {
        Object classes = request.get("objectClass");
        if (classes == null) {
            return List.of();
        }
        if (!(classes instanceof List)) {
            throw ProducerException.illegalArgument("objectClass must be an array");
        }
        List<String> out = new ArrayList<>();
        for (Object c : (List<Object>) classes) {
            out.add(String.valueOf(c));
        }
        return out;
    }

    // --- arg coercion ------------------------------------------------------

    /** A request body as JSON text: a JSON string is taken as already-serialized JSON. */
    private static String body(Object v) {
        if (v == null) {
            return null;
        }
        return v instanceof String ? (String) v : GSON.toJson(v);
    }

    /** 400 {@code UnsupportedOperationError} when any of {@code params} is set (empty = unset). */
    private static void rejectSet(String method, Map<String, Object> argMap, String... params) {
        for (String p : params) {
            if (isSet(argMap.get(p))) {
                throw ProducerException.unsupported(p + " is not supported by " + method + " on this producer");
            }
        }
    }

    private static boolean isSet(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof String) {
            return !((String) v).isEmpty();
        }
        if (v instanceof Collection) {
            return !((Collection<?>) v).isEmpty();
        }
        return true;
    }

    /** The {@code CreateObjectRequest} fields a mkdir honours; any other one set is a 400. */
    private static final Set<String> CREATE_CHILD_FIELDS = Set.of("id", "name", "objectClass");

    private static String[] unhonoured(Collection<String> fields) {
        List<String> out = new ArrayList<>();
        for (String f : fields) {
            if (!CREATE_CHILD_FIELDS.contains(f)) {
                out.add(f);
            }
        }
        return out.toArray(new String[0]);
    }

    /** {@code scope}: {@code one_level} (the default, and all a child listing can be) or unset. */
    private static void requireOneLevelScope(String method, Map<String, Object> argMap) {
        String scope = str(argMap, "scope");
        if (scope == null || scope.isEmpty() || "one_level".equals(scope)) {
            return;
        }
        if ("subtree".equals(scope)) {
            throw ProducerException.unsupported("scope=subtree is not supported by " + method + " on this producer");
        }
        throw ProducerException.illegalArgument("scope must be one of " + List.of("one_level", "subtree"));
    }

    /**
     * {@code sortBy}/{@code sortDir}: the interface declares arrays; one sort key is
     * implemented, so a single value (bare or a one-element array) is taken and more than one
     * is a 400 rather than silently sorting by the first.
     */
    private static String sortArg(Map<String, Object> argMap, String key) {
        Object v = argMap.get(key);
        if (v instanceof Collection) {
            Collection<?> c = (Collection<?>) v;
            if (c.isEmpty()) {
                return null;
            }
            if (c.size() > 1) {
                throw ProducerException.unsupported(
                    key + " takes one sort key on this producer, got " + c.size());
            }
            Object only = c.iterator().next();
            return only == null ? null : only.toString();
        }
        return v == null ? null : v.toString();
    }

    private static int pageNumber(Map<String, Object> argMap) {
        Integer n = intArg(argMap, "pageNumber");
        return n == null ? 1 : n;
    }

    private static int pageSize(Map<String, Object> argMap) {
        Integer n = intArg(argMap, "pageSize");
        return n == null ? X12ProducerFacade.DEFAULT_PAGE_SIZE : n;
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    /** An integer argument: a whole number or its decimal string; unset → null; anything else is a 400. */
    private static Integer intArg(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null || (v instanceof String && ((String) v).isBlank())) {
            return null;
        }
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                return (int) d;
            }
        } else if (v instanceof String) {
            try {
                return Integer.parseInt(((String) v).trim());
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
