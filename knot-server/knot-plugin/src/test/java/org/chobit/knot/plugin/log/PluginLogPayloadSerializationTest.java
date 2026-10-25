package org.chobit.knot.plugin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.chobit.knot.gateway.plugin.gateway.GatewayRoutingSnapshot;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.plugin.log.*;
import org.junit.jupiter.api.Test;

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
                "OPENAI", "gpt-4o", "OPENAI", "OPENAI", "application/json", null,
                JsonKit.toTree(Map.of("stream", true)), null);

        JsonNode json = mapper.valueToTree(payload);
        assertEquals("provider-request", json.get("payloadType").asText());
        assertEquals("OPENAI", json.get("providerCode").asText());
        assertEquals("gpt-4o", json.get("modelCode").asText());
        assertEquals("OPENAI", json.get("bindingProtocol").asText());
        assertTrue(json.get("requestBody").get("stream").asBoolean());
        // UPSTREAM_REQUEST 阶段上游未响应，恒为 null——字段占位保三阶段格式一致
        assertTrue(json.get("upstreamRequestId").isNull());
        // 请求阶段已可算出上游地址，但本次测试不校验具体值，仅确认字段存在且为 null
        assertTrue(json.get("upstreamUrl").isNull());
    }

    @Test
    void shouldTagUpstreamResponsePayload() throws Exception {
        PluginLogPayload payload = new UpstreamResponsePayload(
                "OPENAI", "gpt-4o", "OPENAI", "req_abc123",
                JsonKit.toTree(Map.of("usage", Map.of("total_tokens", 42))),
                "https://openrouter.ai/api/v1/chat/completions");

        JsonNode json = mapper.valueToTree(payload);
        assertEquals("provider-response", json.get("payloadType").asText());
        assertEquals(42, json.get("response").get("usage").get("total_tokens").asInt());
        assertEquals("req_abc123", json.get("upstreamRequestId").asText());
    }

    @Test
    void shouldTagUpstreamErrorPayload() throws Exception {
        PluginLogPayload payload = new UpstreamErrorPayload(
                "OPENAI", "gpt-4o", "OPENAI", "req_abc123", "IllegalStateException", "boom",
                JsonKit.toTree(Map.of("model", "gpt-4o")),
                "https://openrouter.ai/api/v1/chat/completions");

        JsonNode json = mapper.valueToTree(payload);
        assertEquals("provider-error", json.get("payloadType").asText());
        assertEquals("IllegalStateException", json.get("errorType").asText());
        assertEquals("boom", json.get("errorMessage").asText());
        // 排障工单 90% 在错误场景：失败日志的供应商 id 是最关键的检索键
        assertEquals("req_abc123", json.get("upstreamRequestId").asText());
    }
}
