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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JSON 序列化 / 反序列化工具，统一项目内 Jackson 使用。
 * <p>
 * 默认使用内置 {@link ObjectMapper}；Spring 容器启动后由 {@code JsonKitConfiguration}
 * 调用 {@link #init(ObjectMapper)}，与 Spring MVC 的 Jackson 配置对齐。
 */
public final class JsonKit {

    private static final Logger log = LoggerFactory.getLogger(JsonKit.class);

    private static final ObjectMapper DEFAULT_MAPPER = createDefaultMapper();

    private static volatile ObjectMapper mapper = DEFAULT_MAPPER;

    private static volatile boolean springInitialized;

    private JsonKit() {
    }

    /**
     * 使用 Spring Boot 自动配置的 {@link ObjectMapper} 初始化（通常仅调用一次）。
     * 采用 {@link ObjectMapper#copy()}，避免与容器内 Bean 共享可变状态。
     */
    public static synchronized void init(ObjectMapper springMapper) {
        if (springMapper == null) {
            return;
        }
        mapper = springMapper.copy();
        springInitialized = true;
        log.debug("JsonKit initialized from Spring ObjectMapper");
    }

    /** 是否已由 Spring 完成初始化 */
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

    /** 返回当前 Mapper 的独立副本 */
    public static ObjectMapper mapper() {
        return mapper.copy();
    }

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

    public static ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    public static ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }
}
