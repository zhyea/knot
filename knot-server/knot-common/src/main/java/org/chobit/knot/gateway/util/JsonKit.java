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
 * Creates a new resource. Executes the public operation.
 */
/**
 * JSON 搴忓垪鍖?/ 鍙嶅簭鍒楀寲宸ュ叿锛岀粺涓€椤圭洰鍐?Jackson 浣跨敤銆?
 * <p>
 * 榛樿浣跨敤鍐呯疆 {@link ObjectMapper}锛汼pring 瀹瑰櫒鍚姩鍚庣敱 {@code JsonKitConfiguration}
 * 璋冪敤 {@link #init(ObjectMapper)}锛屼笌 Spring MVC 鐨?Jackson 閰嶇疆瀵归綈銆?
 */
@Slf4j
public final class JsonKit {

    private static final ObjectMapper DEFAULT_MAPPER = createDefaultMapper();

    private static volatile ObjectMapper mapper = DEFAULT_MAPPER;

    private static volatile boolean springInitialized;

    private JsonKit() {
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    /**
     * 浣跨敤 Spring Boot 鑷姩閰嶇疆鐨?{@link ObjectMapper} 鍒濆鍖栵紙閫氬父浠呰皟鐢ㄤ竴娆★級銆?
     * 閲囩敤 {@link ObjectMapper#copy()}锛岄伩鍏嶄笌瀹瑰櫒鍐?Bean 鍏变韩鍙彉鐘舵€併€?
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
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    /**
     * 鏄惁宸茬敱 Spring 瀹屾垚鍒濆鍖?
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
     * Executes the public operation. Executes the public operation.
     */
    /**
     * 杩斿洖褰撳墠 Mapper 鐨勭嫭绔嬪壇鏈?
     */
    public static ObjectMapper mapper() {
        return mapper.copy();
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
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
     * Builds the target value from the source input. Executes the public operation.
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
     * Builds the target value from the source input. Executes the public operation.
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
     * Executes the public operation. Executes the public operation.
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
     * Converts the source value to the target representation. Executes the public operation.
     */
    public static Map<String, Object> toMap(Object obj) {
        return mapper.convertValue(obj, new TypeReference<>() {
        });
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    public static ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    public static ArrayNode createArrayNode() {
        return mapper.createArrayNode();
    }
}
