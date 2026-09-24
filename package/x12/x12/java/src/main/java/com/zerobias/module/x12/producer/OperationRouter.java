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
 * exactly; the {@code argMap} keys are the operation's parameter names.
 *
 * <p>Every parameter an operation declares is either honoured or, when set, rejected with
 * {@code UnsupportedOperationError} — never silently dropped. Sorting, cursor paging
 * ({@code pageToken}) and property projection are not implemented: the buffer pages by
 * offset in a fixed newest-first order. {@code getCollectionElements} honours
 * {@code filter} like {@code searchCollectionElements} does (the hl7/v2 contract callers
 * already use), rather than rejecting it.
 *
 * <p>The two binary ops are the exceptions, because their bodies are bytes rather than
 * JSON: {@code BinaryApi.downloadBinary} ({@link #isBinaryDownload}) returns bytes and
 * {@code BinaryApi.uploadBinaryContent} ({@link #isBinaryUpload}) receives them, so the HTTP
 * layer serves both itself ({@link X12ProducerFacade#downloadBinary} /
 * {@link X12ProducerFacade#uploadBinary}) and this router rejects them.
 *
 * <p>The file-management ops ({@code createChildObject}, {@code deleteObject},
 * {@code uploadBinaryContent}) are routed, but the facade refuses them unless
 * {@code config.allowFileManagement} is set, and {@link #isSupported} answers from the same
 * flag (DESIGN §2.9).
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

    private static final Set<String> SUPPORTED_OPERATION_IDS = operationIds(SUPPORTED);

    /** Supported only while {@code config.allowFileManagement} is set (DESIGN §2.9). */
    private static final Set<String> FILE_MANAGEMENT = Set.of(
        "ObjectsApi.createChildObject",
        "ObjectsApi.deleteObject",
        UPLOAD_BINARY);

    private static final Set<String> FILE_MANAGEMENT_OPERATION_IDS = operationIds(FILE_MANAGEMENT);

    /** The {@code CreateObjectRequest} fields a mkdir honours; any other one set is a 400. */
    private static final Set<String> CREATE_CHILD_FIELDS = Set.of("id", "name", "objectClass");

    private OperationRouter() {
    }

    /** Whether {@code method} is the binary download, which the HTTP layer serves itself. */
    public static boolean isBinaryDownload(String method) {
        return DOWNLOAD_BINARY.equals(method);
    }

    /**
     * {@code isSupported}: true for the read operations routed here, and for the
     * file-management ops only when {@code fileManagement} ({@code config.allowFileManagement})
     * is set. Accepts the bare OpenAPI operationId ({@code getChildren}) or the qualified RPC
     * name ({@code ObjectsApi.getChildren}); data writes and anything unrouted are false.
     */
    public static boolean isSupported(String operationId, boolean fileManagement) {
        if (operationId == null) {
            return false;
        }
        if (SUPPORTED.contains(operationId) || SUPPORTED_OPERATION_IDS.contains(operationId)) {
            return true;
        }
        return fileManagement
            && (FILE_MANAGEMENT.contains(operationId) || FILE_MANAGEMENT_OPERATION_IDS.contains(operationId));
    }

    private static Set<String> operationIds(Set<String> qualified) {
        Set<String> out = new LinkedHashSet<>();
        for (String q : qualified) {
            out.add(q.substring(q.indexOf('.') + 1));
        }
        return Set.copyOf(out);
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
                return objects(facade, method, methodName, argMap);
            case "CollectionsApi":
                return collections(facade, method, methodName, argMap);
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

    private static String objects(X12ProducerFacade facade, String method, String methodName,
            Map<String, Object> argMap) throws Exception {
        switch (methodName) {
            case "getRootObject":
                return facade.getRootObject();
            case "getObject":
                return facade.getObject(str(argMap, "objectId"));
            case "getChildren":
                rejectSet(method, argMap, "sortBy", "sortDir", "type", "tags", "pageToken");
                return facade.getChildren(str(argMap, "objectId"), pageNumber(argMap), pageSize(argMap));
            case "searchChildObjects":
                // No child filter/projection/sort; count is always in the body, so includeCount holds.
                rejectSet(method, argMap, "sortBy", "sortDir", "filter", "pageToken", "properties");
                requireOneLevelScope(method, argMap);
                requireBoolean(argMap, "includeCount");
                return facade.getChildren(str(argMap, "objectId"), pageNumber(argMap), pageSize(argMap));
            case "createChildObject": {
                facade.requireFileManagement(methodName);   // disabled = unsupported, whatever the args
                Map<String, Object> request = createObjectRequest(argMap);
                rejectSet(method, request, unhonoured(request.keySet()));
                String name = str(request, "name");
                return facade.createChildObject(str(argMap, "objectId"), name != null ? name : str(request, "id"),
                    childClasses(request));
            }
            case "deleteObject":
                facade.requireFileManagement(methodName);
                requireBoolean(argMap, "recursive");
                if (Boolean.TRUE.equals(argMap.get("recursive")) || "true".equals(argMap.get("recursive"))) {
                    throw ProducerException.unsupported("recursive=true is not supported by " + method
                        + " on this producer: delete a directory's children first");
                }
                return facade.deleteObject(str(argMap, "objectId"));
            default:
                throw ProducerException.unsupported("Unsupported ObjectsApi method: " + methodName);
        }
    }

    private static String collections(X12ProducerFacade facade, String method, String methodName,
            Map<String, Object> argMap) throws Exception {
        switch (methodName) {
            case "getCollectionElements":
            case "searchCollectionElements":
                rejectSet(method, argMap, "sortBy", "sortDir", "pageToken", "properties");
                return facade.getCollectionElements(str(argMap, "objectId"), str(argMap, "filter"),
                    pageNumber(argMap), pageSize(argMap));
            case "getCollectionElement":
                return facade.getCollectionElement(str(argMap, "objectId"), str(argMap, "elementKey"));
            case "addCollectionElement":
                throw ProducerException.unsupported(
                    "Collection is receive-only; transactions arrive as inbox files, not via addCollectionElement");
            case "updateCollectionElement":
                throw ProducerException.unsupported("Collection is read-only");
            case "deleteCollectionElement":
                throw ProducerException.unsupported("Collection is read-only; use ops/purge to evict acked rows");
            default:
                throw ProducerException.unsupported("Unsupported CollectionsApi method: " + methodName);
        }
    }

    private static String schemas(X12ProducerFacade facade, String methodName, Map<String, Object> argMap) {
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

    private static String[] unhonoured(Collection<String> fields) {
        List<String> out = new ArrayList<>();
        for (String f : fields) {
            if (!CREATE_CHILD_FIELDS.contains(f)) {
                out.add(f);
            }
        }
        return out.toArray(new String[0]);
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
