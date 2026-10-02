package org.chobit.knot.gateway.adapter.request;

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anthropic Adapter 跨协议转换的回归测试（清单 §2.1）。
 *
 * <p>盯四件事：① 同协议（MESSAGES）原样透传；② 转换不原地改写用户请求；
 * ③ 只产生目标协议要求的差异，未知字段保留；④ 已有字段不被别名转换覆盖，缺失时才补默认值。
 */
class AnthropicRequestAdapterTest {

    private final AnthropicRequestAdapter adapter = new AnthropicRequestAdapter();

    @Test
    void shouldPassThroughMessagesProtocol() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MESSAGES, List.of(Map.of(AiPayloadFields.ROLE, "user", "content", "hi")));
        source.put("vendor_only_field", "keep-me");

        UpstreamRequestContext ctx = context(ModelApiProtocolEnum.MESSAGES, source);
        Object result = adapter.buildRequestBody(ctx);

        assertSame(ctx.requestBody(), result, "MESSAGES 协议无需转换，应原样返回");
        assertEquals("keep-me", asMap(result).get("vendor_only_field"));
    }

    @Test
    void shouldNotMutateOriginalRequestBody() {
        Map<String, Object> source = chatCompletionsBody();

        adapter.buildRequestBody(context(ModelApiProtocolEnum.CHAT_COMPLETIONS, source));

        assertTrue(source.containsKey(AiPayloadFields.MAX_COMPLETION_TOKENS), "原始请求仍应保留 max_completion_tokens");
        assertTrue(source.containsKey(AiPayloadFields.STOP), "原始请求仍应保留 stop");
        assertFalse(source.containsKey(AiPayloadFields.STOP_SEQUENCES), "原始请求不应被写入 stop_sequences");
        assertFalse(source.containsKey(AiPayloadFields.SYSTEM), "原始请求不应被写入 system");
    }

    @Test
    void shouldOnlyChangeFieldsRequiredByAnthropicProtocol() {
        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.CHAT_COMPLETIONS, chatCompletionsBody())));

        // system 角色的消息提升为顶层 system
        assertEquals("你是一个助手", result.get(AiPayloadFields.SYSTEM));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(1, messages.size(), "system 消息已从 messages 中移除");
        assertEquals("user", messages.get(0).get(AiPayloadFields.ROLE));

        // 别名迁移：max_completion_tokens -> max_tokens、stop -> stop_sequences
        assertEquals(2048, result.get(AiPayloadFields.MAX_TOKENS));
        assertFalse(result.containsKey(AiPayloadFields.MAX_COMPLETION_TOKENS));
        assertEquals(List.of("###"), result.get(AiPayloadFields.STOP_SEQUENCES));
        assertFalse(result.containsKey(AiPayloadFields.STOP));

        // Anthropic 不支持的字段被移除
        assertFalse(result.containsKey("n"));
        assertFalse(result.containsKey("presence_penalty"));
        assertFalse(result.containsKey("response_format"));

        // 非协议冲突字段保留
        assertEquals(0.7, result.get("temperature"));
        assertEquals("keep-me", result.get("vendor_only_field"));
    }

    @Test
    void shouldNotOverwriteExistingMaxTokens() {
        Map<String, Object> source = chatCompletionsBody();
        source.put(AiPayloadFields.MAX_TOKENS, 512);

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.CHAT_COMPLETIONS, source)));

        assertEquals(512, result.get(AiPayloadFields.MAX_TOKENS), "目标字段已存在时不应用别名覆盖");
        assertFalse(result.containsKey(AiPayloadFields.MAX_COMPLETION_TOKENS));
    }

    @Test
    void shouldDefaultMaxTokensWhenAbsent() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "hi"))));

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.CHAT_COMPLETIONS, source)));

        assertEquals(1024, result.get(AiPayloadFields.MAX_TOKENS), "缺失时补 Anthropic 要求的默认值");
    }

    @Test
    void shouldUseEmptyContentWhenPromptIsNull() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.PROMPT, null);

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.COMPLETIONS, source)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(1, messages.size());
        assertEquals("", messages.get(0).get(AiPayloadFields.CONTENT), "null prompt 按空串处理，不产出 null");
        assertFalse(result.containsKey(AiPayloadFields.PROMPT));
    }

    private Map<String, Object> chatCompletionsBody() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, AiPayloadFields.SYSTEM, AiPayloadFields.CONTENT, "你是一个助手"),
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "你好"))));
        source.put(AiPayloadFields.MAX_COMPLETION_TOKENS, 2048);
        source.put(AiPayloadFields.STOP, List.of("###"));
        source.put("n", 1);
        source.put("presence_penalty", 0.1);
        source.put("response_format", Map.of("type", "json_object"));
        source.put("temperature", 0.7);
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
