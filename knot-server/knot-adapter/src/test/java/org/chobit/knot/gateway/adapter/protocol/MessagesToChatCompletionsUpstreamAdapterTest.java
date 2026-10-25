package org.chobit.knot.gateway.adapter.protocol;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Messages → ChatCompletions 适配器的转换回归测试（泛化转换，仅服务 MESSAGES 入协议）。
 *
 * <p>盯四件事：① 只产生目标协议要求的差异，未知字段保留；② 不原地改写用户请求；
 * ③ 已有字段不被别名转换覆盖；④ null system 不产出 system 消息。</p>
 */
class MessagesToChatCompletionsUpstreamAdapterTest {

    private final MessagesToChatCompletionsUpstreamAdapter adapter = new MessagesToChatCompletionsUpstreamAdapter();

    @Test
    void shouldExposeStableCodeAndLabel() {
        assertEquals("MESSAGES_TO_CHAT_COMPLETIONS", adapter.code());
        assertEquals("Messages 转 ChatCompletions（OpenAI 兼容）", adapter.label());
    }

    @Test
    void shouldDeclareMessagesProtocolOnly() {
        assertEquals(1, adapter.protocol().size(), "一适配器一协议：仅服务 MESSAGES");
        assertTrue(adapter.protocol().contains(ModelApiProtocolEnum.MESSAGES));
    }

    @Test
    void shouldOnlyChangeFieldsRequiredByTargetProtocol() {
        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.MESSAGES, anthropicStyleBody())));

        // system 进入 messages 首位
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get(AiPayloadFields.ROLE));
        assertEquals("你是一个助手", messages.get(0).get(AiPayloadFields.CONTENT));
        assertEquals("user", messages.get(1).get(AiPayloadFields.ROLE));

        // stop_sequences -> stop
        assertEquals(List.of("###"), result.get(AiPayloadFields.STOP));
        assertFalse(result.containsKey(AiPayloadFields.STOP_SEQUENCES));

        // Anthropic 专属字段被移除
        assertFalse(result.containsKey(AiPayloadFields.SYSTEM));
        assertFalse(result.containsKey("top_k"));

        // 非协议冲突的字段必须原样保留
        assertEquals(1024, result.get(AiPayloadFields.MAX_TOKENS));
        assertEquals("keep-me", result.get("vendor_only_field"));
    }

    @Test
    void shouldNotOverwriteExistingStopField() {
        Map<String, Object> source = anthropicStyleBody();
        source.put(AiPayloadFields.STOP, List.of("END"));

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.MESSAGES, source)));

        assertEquals(List.of("END"), result.get(AiPayloadFields.STOP), "目标字段已存在时不应用别名覆盖");
        assertFalse(result.containsKey(AiPayloadFields.STOP_SEQUENCES));
    }

    @Test
    void shouldNotMutateOriginalRequestBody() {
        Map<String, Object> source = anthropicStyleBody();

        adapter.buildRequestBody(context(ModelApiProtocolEnum.MESSAGES, source));

        assertTrue(source.containsKey(AiPayloadFields.SYSTEM), "原始请求仍应保留 system");
        assertTrue(source.containsKey(AiPayloadFields.STOP_SEQUENCES), "原始请求仍应保留 stop_sequences");
        assertFalse(source.containsKey(AiPayloadFields.STOP), "原始请求不应被写入 stop");
    }

    @Test
    void shouldSkipSystemMessageWhenSystemIsNull() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "hi"))));
        source.put(AiPayloadFields.SYSTEM, null);

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.MESSAGES, source)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(1, messages.size(), "null system 不应产出 system 消息");
        assertEquals("user", messages.get(0).get(AiPayloadFields.ROLE));
    }

    private Map<String, Object> anthropicStyleBody() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.SYSTEM, "你是一个助手");
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "你好"))));
        source.put(AiPayloadFields.MAX_TOKENS, 1024);
        source.put(AiPayloadFields.STOP_SEQUENCES, List.of("###"));
        source.put("top_k", 40);
        source.put("vendor_only_field", "keep-me");
        return source;
    }

    private UpstreamRequestContext context(ModelApiProtocolEnum protocol, Map<String, Object> body) {
        return new UpstreamRequestContext(protocol, body, null, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
