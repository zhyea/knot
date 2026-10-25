package org.chobit.knot.adapter.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.adapter.protocol.CompletionsToMessagesUpstreamAdapter;
import org.chobit.knot.adapter.protocol.StreamLineAdapter;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.util.JsonKit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completions → Messages 适配器回归测试（仅服务 COMPLETIONS 入协议）。
 *
 * <p>盯四件事：① {@code prompt} 折成单条 user 消息；② null prompt 按空串处理；
 * ③ {@code stop→stop_sequences}、Completions 专有字段移除；④ 不原地改写用户请求。</p>
 */
class CompletionsToMessagesUpstreamAdapterTest {

    private final CompletionsToMessagesUpstreamAdapter adapter = new CompletionsToMessagesUpstreamAdapter();

    @Test
    void shouldExposeStableCodeAndLabel() {
        assertEquals("COMPLETIONS_TO_MESSAGES", adapter.code());
        assertEquals("Completions 转 Messages", adapter.label());
    }

    @Test
    void shouldDeclareCompletionsProtocolOnly() {
        assertEquals(1, adapter.protocol().size(), "一适配器一协议：仅服务 COMPLETIONS");
        assertTrue(adapter.protocol().contains(ModelApiProtocolEnum.COMPLETIONS));
    }

    @Test
    void shouldFoldPromptIntoUserMessage() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.PROMPT, "你好");
        source.put(AiPayloadFields.STOP, List.of("###"));
        source.put("max_tokens", 512);

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.COMPLETIONS, source)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).get(AiPayloadFields.ROLE));
        assertEquals("你好", messages.get(0).get(AiPayloadFields.CONTENT));

        assertEquals(List.of("###"), result.get(AiPayloadFields.STOP_SEQUENCES));
        assertFalse(result.containsKey(AiPayloadFields.PROMPT));
        assertFalse(result.containsKey("suffix"));
        assertFalse(result.containsKey("logprobs"));
        assertEquals(512, result.get(AiPayloadFields.MAX_TOKENS), "已有 max_tokens 不被兜底覆盖");
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

    @Test
    void shouldNotMutateOriginalRequestBody() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.PROMPT, "你好");

        adapter.buildRequestBody(context(ModelApiProtocolEnum.COMPLETIONS, source));

        assertTrue(source.containsKey(AiPayloadFields.PROMPT), "原始请求仍应保留 prompt");
        assertFalse(source.containsKey(AiPayloadFields.MESSAGES), "原始请求不应被写入 messages");
    }

    @Test
    void shouldConvertMessagesResponseToTextCompletion() {
        Map<String, Object> result = readMap(adapter.handleResponse(
                "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-sonnet-4\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}],\"stop_reason\":\"max_tokens\","
                        + "\"usage\":{\"input_tokens\":12,\"output_tokens\":7}}",
                context(ModelApiProtocolEnum.COMPLETIONS, Map.of())));

        assertEquals("text_completion", result.get("object"));
        Map<String, Object> choice = asMap(((List<?>) result.get("choices")).get(0));
        assertEquals("Hello", choice.get("text"));
        assertEquals("length", choice.get("finish_reason"), "max_tokens → length");
        Map<String, Object> usage = asMap(result.get(AiPayloadFields.USAGE));
        assertEquals(12L, ((Number) usage.get(AiPayloadFields.PROMPT_TOKENS)).longValue());
        assertEquals(19L, ((Number) usage.get(AiPayloadFields.TOTAL_TOKENS)).longValue());
    }

    @Test
    void shouldPassThroughNonMessagesResponses() {
        String error = "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}";
        assertEquals(error, adapter.handleResponse(error, context(ModelApiProtocolEnum.COMPLETIONS, Map.of())));
    }

    @Test
    @DisplayName("旧版 Completions 没有工具概念：调用方硬传的 tools 必须剔除")
    void shouldDropToolsForLegacyCompletions() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.PROMPT, "你好");
        source.put(AiPayloadFields.TOOLS, List.of(Map.of(AiPayloadFields.NAME, "get_weather")));
        source.put(AiPayloadFields.TOOL_CHOICE, "auto");

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.COMPLETIONS, source)));

        assertFalse(result.containsKey(AiPayloadFields.TOOLS), "原样带给 Anthropic 会 400");
        assertFalse(result.containsKey(AiPayloadFields.TOOL_CHOICE));
    }

    @Test
    @DisplayName("流式走 text_completion chunk 转换器（非默认透传）")
    void shouldProvideTextCompletionStreamAdapter() {
        StreamLineAdapter streamAdapter = adapter.streamAdapter(
                context(ModelApiProtocolEnum.COMPLETIONS, Map.of()));

        assertNotSame(StreamLineAdapter.PASSTHROUGH, streamAdapter);
        assertEquals(List.of(), streamAdapter.adapt("event: content_block_start"), "event 行应被抑制");
        assertEquals(List.of("data: [DONE]"), streamAdapter.adapt("data: {\"type\":\"message_stop\"}"));
    }

    private Map<String, Object> readMap(String json) {
        return JsonKit.fromJson(json, new TypeReference<>() {
        });
    }

    private UpstreamRequestContext context(ModelApiProtocolEnum protocol, Map<String, Object> body) {
        return new UpstreamRequestContext(protocol, body, null, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
