package org.chobit.knot.gateway.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

/**
 * JSON 序列化与反序列化工具，统一项目内 Jackson 的使用方式。
 */
@Slf4j
public final class JsonKit {

    private static final ObjectMapper DEFAULT_MAPPER = createDefaultMapper();
    private static volatile ObjectMapper mapper = DEFAULT_MAPPER;
    private static volatile boolean springInitialized;

    private JsonKit() {
    }

    /**
     * 使用 Spring Boot 的 ObjectMapper 初始化工具类。
     */
    public static synchronized void init(ObjectMapper springMapper) {
        if (springMapper == null) {
            return;
        }
        mapper = springMapper.copy();
        springInitialized = true;
        log.debug("JsonKit initialized from Spring ObjectMapper");
    }

    /**
     * Returns whether JsonKit has been initialized from Spring.
     */
    public static boolean isInitializedFromSpring() {
        return springInitialized;
    }

    private static ObjectMapper createDefaultMapper() {
        ObjectMapper m = new ObjectMapper();
        m.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        m.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        m.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        m.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        m.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        m.registerModule(new JavaTimeModule());
        return m;
    }

    /**
     * Returns a defensive copy of the current mapper.
     */
    public static ObjectMapper mapper() {
        return mapper.copy();
    }

    /**
     * Serializes an object to JSON.
     */
    public static <T> String toJson(T src) {
        if (src == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(src);
        } catch (Exception e) {
            log.error("Serialize object to json failed", e);
            return null;
        }
    }

    /**
     * Deserializes JSON to a concrete class.
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Deserialize json to {} failed, json: {}", clazz.getSimpleName(), json, e);
            return null;
        }
    }

    /**
     * Deserializes JSON using a type reference.
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.error("Deserialize json failed, json: {}", json, e);
            return null;
        }
    }

    /**
     * Parses JSON to a tree node.
     */
    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            log.error("Parse json tree failed, json: {}", json, e);
            return null;
        }
    }

    /**
     * Converts an object to a map.
     */
    public static Map<String, Object> toMap(Object obj) {
        return mapper.convertValue(obj, new TypeReference<>() {
        });
    }

    /**
     * Creates an object node.
     */
    public static ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    /**
     * Creates an array node.
     */
    public static ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }
}
