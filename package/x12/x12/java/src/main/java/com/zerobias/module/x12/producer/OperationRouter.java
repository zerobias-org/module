package com.zerobias.module.x12.producer;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Routes DataProducer operation invocations ({@code ApiClass.methodName}, e.g.
 * {@code ObjectsApi.getRootObject}) to {@link X12ProducerFacade} methods — the same
 * dispatch contract the SQL generic module and hl7/v2 use, so the Hub Node's
 * java-http invoker drives this module unchanged.
 *
 * <p>The two binary ops are the exceptions, because their bodies are bytes rather than
 * JSON: download ({@link #isBinaryDownload}) returns bytes and upload
 * ({@link #isBinaryUpload}) receives them, so the HTTP layer short-circuits both to
 * {@link X12ProducerFacade#downloadBinary} / {@link X12ProducerFacade#uploadBinary} and
 * this router rejects them.
 */
public final class OperationRouter {

    private static final Gson GSON = new Gson();

    private OperationRouter() {
    }

    /**
     * Whether {@code method} is the binary download op ({@code BinaryApi.downloadBinaryContent};
     * the bare {@code downloadBinary} name is accepted too). The HTTP layer must serve the
     * bytes itself — {@link #executeOperation} rejects it.
     */
    public static boolean isBinaryDownload(String method) {
        return method != null && (method.endsWith(".downloadBinaryContent") || method.endsWith(".downloadBinary"));
    }

    /**
     * Whether {@code method} is the binary upload op ({@code BinaryApi.uploadBinaryContent};
     * the bare {@code uploadBinary} name is accepted too). Its request body is raw bytes
     * rather than the JSON {@code argMap} envelope, so the HTTP layer must read the body
     * itself — {@link #executeOperation} rejects it, symmetrically with download.
     */
    public static boolean isBinaryUpload(String method) {
        return method != null && (method.endsWith(".uploadBinaryContent") || method.endsWith(".uploadBinary"));
    }

    private static boolean isUploadName(String methodName) {
        return "uploadBinaryContent".equals(methodName) || "uploadBinary".equals(methodName);
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
            case "searchChildObjects":
                return facade.getChildren(
                    str(argMap, "objectId"),
                    getInt(argMap, "pageSize", 100),
                    getInt(argMap, "pageNumber", 1));
            case "createChildObject":
                return facade.createChildObject(
                    str(argMap, "objectId"), childName(argMap), childClasses(argMap));
            case "deleteObject":
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
                return facade.getCollectionElements(
                    str(argMap, "objectId"),
                    str(argMap, "filter"),
                    str(argMap, "sortBy"),
                    str(argMap, "sortDir"),
                    getInt(argMap, "pageSize", 100),
                    getInt(argMap, "pageNumber", 1),
                    str(argMap, "pageToken"));
            case "getCollectionElement":
                return facade.getCollectionElement(
                    str(argMap, "objectId"), str(argMap, "elementKey"));
            case "addCollectionElement":
                return facade.createCollectionElement(
                    str(argMap, "objectId"), GSON.toJson(argMap.get("element")));
            case "updateCollectionElement":
                return facade.updateCollectionElement(
                    str(argMap, "objectId"), str(argMap, "elementKey"),
                    GSON.toJson(argMap.get("element")));
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
            String schemaId = str(argMap, "objectId");
            if (schemaId == null) {
                schemaId = str(argMap, "schemaId");
            }
            return facade.getSchema(schemaId);
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
            case "getDocument":
                return facade.getDocumentData(str(argMap, "objectId"));
            case "updateDocumentData":
            case "updateDocument":
                throw ProducerException.unsupported("Documents are read-only: " + methodName);
            default:
                throw ProducerException.unsupported("Unsupported DocumentsApi method: " + methodName);
        }
    }

    private static String binary(String methodName) {
        if (isUploadName(methodName)) {
            throw ProducerException.illegalArgument(
                "uploadBinaryContent carries raw bytes and is served by the HTTP layer, not the JSON router");
        }
        if ("downloadBinaryContent".equals(methodName) || "downloadBinary".equals(methodName)) {
            throw ProducerException.illegalArgument(
                "downloadBinary is served by the HTTP layer (bytes), not the JSON router");
        }
        throw ProducerException.unsupported("Unsupported BinaryApi method: " + methodName);
    }

    // --- createChildObject args (CreateObjectRequest) ----------------------

    /**
     * The new child's name: {@code name}, or {@code id} (the interface's
     * {@code CreateObjectRequest} makes {@code id} optional and generated, and callers
     * send one or the other). Taken from {@code object}/{@code body} when the caller nests
     * the request rather than flattening it into the argMap.
     */
    private static String childName(Map<String, Object> argMap) {
        Map<String, Object> req = nested(argMap);
        String name = str(req, "name");
        return name != null ? name : str(req, "id");
    }

    @SuppressWarnings("unchecked")
    private static List<String> childClasses(Map<String, Object> argMap) {
        Object classes = nested(argMap).get("objectClass");
        if (classes instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object c : (List<Object>) classes) {
                out.add(String.valueOf(c));
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> argMap) {
        for (String key : new String[] {"object", "body", "requestBody", "createObjectRequest"}) {
            Object v = argMap.get(key);
            if (v instanceof Map) {
                return (Map<String, Object>) v;
            }
        }
        return argMap;
    }

    // --- arg coercion ------------------------------------------------------

    /** A request body as JSON text: a JSON string is taken as already-serialized JSON. */
    private static String body(Object v) {
        if (v == null) {
            return null;
        }
        return v instanceof String ? (String) v : GSON.toJson(v);
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return Integer.parseInt((String) v);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
