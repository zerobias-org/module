package com.zerobias.module.hl7.producer;

import com.google.gson.Gson;

import java.util.Map;

/**
 * Routes DataProducer operation invocations ({@code ApiClass.methodName}, e.g.
 * {@code ObjectsApi.getRootObject}) to {@link Hl7ProducerFacade} methods — the
 * same dispatch contract the SQL generic module uses, so the Hub Node's
 * java-http invoker drives this module unchanged.
 *
 * <p>Read ops are implemented; the write surface is rejected by the facade with
 * {@code UnsupportedOperationError}; functions land in Phase 7.
 */
public final class OperationRouter {

    private static final Gson GSON = new Gson();

    private OperationRouter() {
    }

    public static String executeOperation(Hl7ProducerFacade facade, String method,
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
            default:
                throw ProducerException.unsupported("Unknown API class: " + apiClass);
        }
    }

    private static String objects(Hl7ProducerFacade facade, String methodName,
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
                    getInt(argMap, "pageNumber", 1) - 1);
            case "createChildObject":
            case "updateObject":
            case "deleteObject":
                throw ProducerException.unsupported(
                    "Object tree is fixed (receive-only): " + methodName);
            default:
                throw ProducerException.unsupported("Unsupported ObjectsApi method: " + methodName);
        }
    }

    private static String collections(Hl7ProducerFacade facade, String methodName,
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
                    getInt(argMap, "pageNumber", 1) - 1,
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

    private static String schemas(Hl7ProducerFacade facade, String methodName,
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

    private static String functions(Hl7ProducerFacade facade, String methodName,
            Map<String, Object> argMap) throws Exception {
        if ("invokeFunction".equals(methodName)) {
            Object input = argMap.get("requestBody");
            String inputJson = input == null ? "{}"
                : (input instanceof String ? (String) input : GSON.toJson(input));
            return facade.invokeFunction(str(argMap, "objectId"), inputJson);
        }
        throw ProducerException.unsupported("Unsupported FunctionsApi method: " + methodName);
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
