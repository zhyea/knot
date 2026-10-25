package org.chobit.knot.gateway.adapter.protocol;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Responses → Messages 适配器回归测试（仅服务 RESPONSES 入协议）。
 *
 * <p>盯四件事：① {@code input} 字符串折成单条 user 消息、事件列表原样作 messages；
 * ② {@code instructions} 提升为顶层 system；③ {@code max_output_tokens→max_tokens}、
 * Responses 专有字段移除；④ 不原地改写用户请求。</p>
 */
class ResponsesToMessagesUpstreamAdapterTest {

    private final ResponsesToMessagesUpstreamAdapter adapter = new ResponsesToMessagesUpstreamAdapter();

    @Test
    void shouldExposeStableCodeAndLabel() {
        assertEquals("RESPONSES_TO_MESSAGES", adapter.code());
        assertEquals("Responses 转 Messages", adapter.label());
    }

    @Test
    void shouldDeclareResponsesProtocolOnly() {
        assertEquals(1, adapter.protocol().size(), "一适配器一协议：仅服务 RESPONSES");
        assertTrue(adapter.protocol().contains(ModelApiProtocolEnum.RESPONSES));
    }

    @Test
    void shouldFoldStringInputIntoUserMessage() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.INPUT, "你好");
        source.put(AiPayloadFields.INSTRUCTIONS, "你是一个助手");
        source.put(AiPayloadFields.MAX_OUTPUT_TOKENS, 2048);

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.RESPONSES, source)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).get(AiPayloadFields.ROLE));
        assertEquals("你好", messages.get(0).get(AiPayloadFields.CONTENT));

        assertEquals("你是一个助手", result.get(AiPayloadFields.SYSTEM), "instructions 提升为 system");
        assertEquals(2048, result.get(AiPayloadFields.MAX_TOKENS), "max_output_tokens 迁移到 max_tokens");
        assertFalse(result.containsKey(AiPayloadFields.MAX_OUTPUT_TOKENS));

        assertFalse(result.containsKey(AiPayloadFields.INPUT));
        assertFalse(result.containsKey(AiPayloadFields.INSTRUCTIONS));
        assertFalse(result.containsKey("previous_response_id"));
        assertFalse(result.containsKey("reasoning"));
    }

    @Test
    void shouldKeepEventListInputAsMessages() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.INPUT, List.of(Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "hi")));

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.RESPONSES, source)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(1, messages.size(), "事件列表原样作 messages");
        assertEquals(1024, result.get(AiPayloadFields.MAX_TOKENS), "缺失时补 Anthropic 要求的默认值");
    }

    @Test
    void shouldNotMutateOriginalRequestBody() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.INPUT, "你好");
        source.put(AiPayloadFields.MAX_OUTPUT_TOKENS, 2048);

        adapter.buildRequestBody(context(ModelApiProtocolEnum.RESPONSES, source));

        assertTrue(source.containsKey(AiPayloadFields.INPUT), "原始请求仍应保留 input");
        assertTrue(source.containsKey(AiPayloadFields.MAX_OUTPUT_TOKENS), "原始请求仍应保留 max_output_tokens");
        assertFalse(source.containsKey(AiPayloadFields.MESSAGES), "原始请求不应被写入 messages");
    }

    private UpstreamRequestContext context(ModelApiProtocolEnum protocol, Map<String, Object> body) {
        return new UpstreamRequestContext(protocol, body, null, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
