package io.docpilot.common.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

public final class JsonUtils {

    private static volatile ObjectMapper objectMapper = DocPilotObjectMapperFactory.create();

    public static void setObjectMapper(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        JsonUtils.objectMapper = objectMapper;
    }

    public static ObjectMapper objectMapper() {
        return objectMapper;
    }

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new JsonException("Failed to serialize object to JSON", e);
        }
    }

    public static <T> T convert(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new JsonException("Failed to deserialize JSON", e);
        }
    }

    public static <T> T convert(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JacksonException e) {
            throw new JsonException("Failed to deserialize JSON", e);
        }
    }

    public static <T> T convert(String json, JavaType javaType) {
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JacksonException e) {
            throw new JsonException("Failed to deserialize JSON", e);
        }
    }

    public static <T> T convertValue(Object anyObject, Class<T> type) {
        return objectMapper.convertValue(anyObject, type);
    }

}
