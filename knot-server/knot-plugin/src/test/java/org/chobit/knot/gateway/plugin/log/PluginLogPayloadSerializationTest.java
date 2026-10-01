package org.chobit.knot.gateway.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.chobit.knot.gateway.plugin.PluginEvent;
import org.chobit.knot.gateway.plugin.PluginEventType;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.plugin.gateway.GatewayRoutingSnapshot;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件日志 payload 的序列化测试。
 *
 * <p>盯四件事：① 六种 payload 各自带 {@code payloadType} 判别值；
 * ② 固定字段名保持稳定；③ 请求/响应里的未知 JSON 字段不丢失；
 * ④ null 错误字段按既有行为输出。
 */
class PluginLogPayloadSerializationTest {

    /** 生产走 Spring 的 ObjectMapper（自带 JSR-310），这里补上模块才能序列化事件信封的时间字段。 */
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldTagGatewayRequestPayload() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-4o");
        body.put("vendor_only_field", "keep-me");

        PluginLogPayload payload = new GatewayRequestPayload(
                "RULE-1", "OPENAI", "application/json",
                JsonKit.toTree(body),
                new GatewayRoutingSnapshot(7L, "RULE-1", 3L, true, 2, "APP-1", "研发"));

        JsonNode json = mapper.valueToTree(payload);
        assertEquals("gateway-request", json.get("payloadType").asText());
        assertEquals("RULE-1", json.get("ruleCode").asText());
        assertEquals("OPENAI", json.get("protocol").asText());
        assertEquals("application/json", json.get("contentType").asText());
        // 未知字段必须原样保留，不能被类型化裁剪掉
        assertEquals("keep-me", json.get("requestBody").get("vendor_only_field").asText());
        assertEquals(7L, json.get("routing").get("ruleId").asLong());
        assertEquals("研发", json.get("routing").get("department").asText());
    }

    @Test
    void shouldTagGatewayResponsePayload() throws Exception {
        PluginLogPayload payload = new GatewayResponsePayload(
                "RULE-1", "OPENAI", JsonKit.toTree(Map.of("choices", 1)));

        JsonNode json = mapper.valueToTree(payload);
        assertEquals("gateway-response", json.get("payloadType").asText());
        assertEquals(1, json.get("responseBody").get("choices").asInt());
    }

    @Test
    void shouldTagGatewayErrorPayloadAndKeepNullErrorFields() throws Exception {
        PluginLogPayload payload = new GatewayErrorPayload(
                "RULE-1", "OPENAI", null, null, JsonKit.toTree(Map.of("model", "gpt-4o")));

        JsonNode json = mapper.valueToTree(payload);
        assertEquals("gateway-error", json.get("payloadType").asText());
        assertTrue(json.has("errorType"));
        assertTrue(json.get("errorType").isNull());
        assertTrue(json.has("errorMessage"));
        assertTrue(json.get("errorMessage").isNull());
    }

    @Test
    void shouldTagUpstreamRequestPayload() throws Exception {
        PluginLogPayload payload = new UpstreamRequestPayload(
                "OPENAI", "gpt-4o", "OPENAI", "OPENAI", "application/json",
                JsonKit.toTree(Map.of("stream", true)));

        JsonNode json = mapper.valueToTree(payload);
        assertEquals("provider-request", json.get("payloadType").asText());
        assertEquals("OPENAI", json.get("providerCode").asText());
        assertEquals("gpt-4o", json.get("modelCode").asText());
        assertEquals("OPENAI", json.get("bindingProtocol").asText());
        assertTrue(json.get("requestBody").get("stream").asBoolean());
    }

    @Test
    void shouldTagUpstreamResponsePayload() throws Exception {
        PluginLogPayload payload = new UpstreamResponsePayload(
                "OPENAI", "gpt-4o", "OPENAI", JsonKit.toTree(Map.of("usage", Map.of("total_tokens", 42))));

        JsonNode json = mapper.valueToTree(payload);
        assertEquals("provider-response", json.get("payloadType").asText());
        assertEquals(42, json.get("response").get("usage").get("total_tokens").asInt());
    }

    @Test
    void shouldTagUpstreamErrorPayload() throws Exception {
        PluginLogPayload payload = new UpstreamErrorPayload(
                "OPENAI", "gpt-4o", "OPENAI", "IllegalStateException", "boom",
                JsonKit.toTree(Map.of("model", "gpt-4o")));

        JsonNode json = mapper.valueToTree(payload);
        assertEquals("provider-error", json.get("payloadType").asText());
        assertEquals("IllegalStateException", json.get("errorType").asText());
        assertEquals("boom", json.get("errorMessage").asText());
    }

    @Test
    void shouldEmitPayloadTypeInsidePluginEventEnvelope() throws Exception {
        PluginEvent event = new PluginEvent(
                PluginEventType.GATEWAY_REQUEST, "trace-1", null,
                "inst-1", "cap-1", PluginStageCode.GATEWAY_REQUEST,
                "GLOBAL", null,
                new GatewayResponsePayload("RULE-1", "OPENAI", null),
                LocalDateTime.of(2026, 10, 2, 0, 0));

        JsonNode json = mapper.valueToTree(event);
        assertEquals("gateway-response", json.get("payload").get("payloadType").asText());
        // 事件信封本身的字段不受 payload 收敛影响
        assertEquals("trace-1", json.get("traceId").asText());
        assertFalse(json.has("payloadType"), "payloadType 只应出现在 payload 内，不应污染事件信封");
    }
}
