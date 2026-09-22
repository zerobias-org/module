package com.zerobias.module.x12.producer;

import com.google.gson.Gson;

import java.util.Map;

/**
 * Routes DataProducer operation invocations ({@code ApiClass.methodName}, e.g.
 * {@code ObjectsApi.getRootObject}) to {@link X12ProducerFacade} methods — the same
 * dispatch contract the SQL generic module and hl7/v2 use, so the Hub Node's
 * java-http invoker drives this module unchanged.
 *
 * <p>Binary download ({@link #isBinaryDownload}) is the one op whose result is not a
 * JSON string; the HTTP layer short-circuits it to {@link X12ProducerFacade#downloadBinary}.
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
            case "updateObject":
            case "deleteObject":
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
        if ("invokeFunction".equals(methodName)) {
            Object input = argMap.get("requestBody");
            String inputJson = input == null ? "{}"
                : (input instanceof String ? (String) input : GSON.toJson(input));
            return facade.invokeFunction(str(argMap, "objectId"), inputJson);
        }
        throw ProducerException.unsupported("Unsupported FunctionsApi method: " + methodName);
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
        if ("uploadBinaryContent".equals(methodName) || "uploadBinary".equals(methodName)) {
            throw ProducerException.unsupported("Files are receive-only; drop them in the inbox, not via upload");
        }
        if ("downloadBinaryContent".equals(methodName) || "downloadBinary".equals(methodName)) {
            throw ProducerException.illegalArgument(
                "downloadBinary is served by the HTTP layer (bytes), not the JSON router");
        }
        throw ProducerException.unsupported("Unsupported BinaryApi method: " + methodName);
    }

    // --- arg coercion ------------------------------------------------------

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
