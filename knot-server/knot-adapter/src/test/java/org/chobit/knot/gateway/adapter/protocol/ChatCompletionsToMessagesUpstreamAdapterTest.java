package org.chobit.knot.gateway.adapter.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChatCompletions → Messages 适配器回归测试（仅服务 CHAT_COMPLETIONS 入协议）。
 *
 * <p>盯四件事：① system 提升；② 别名迁移（{@code max_completion_tokens→max_tokens}、
 * {@code stop→stop_sequences}）；③ Anthropic 不支持字段移除、非冲突字段保留；
 * ④ 已有 {@code max_tokens} 不被覆盖、缺失时补 1024。另守护不原地改写用户请求。</p>
 */
class ChatCompletionsToMessagesUpstreamAdapterTest {

    private final ChatCompletionsToMessagesUpstreamAdapter adapter = new ChatCompletionsToMessagesUpstreamAdapter();

    @Test
    void shouldExposeStableCodeAndLabel() {
        assertEquals("CHAT_COMPLETIONS_TO_MESSAGES", adapter.code());
        assertEquals("ChatCompletions 转 Messages", adapter.label());
    }

    @Test
    void shouldDeclareChatCompletionsProtocolOnly() {
        assertEquals(1, adapter.protocol().size(), "一适配器一协议：仅服务 CHAT_COMPLETIONS");
        assertTrue(adapter.protocol().contains(ModelApiProtocolEnum.CHAT_COMPLETIONS));
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
    void shouldConvertMessagesResponseToChatCompletion() {
        Map<String, Object> result = readMap(adapter.handleResponse(messagesResponse("end_turn"),
                context(ModelApiProtocolEnum.CHAT_COMPLETIONS, Map.of())));

        assertEquals("chat.completion", result.get("object"));
        Map<String, Object> choice = asMap(((List<?>) result.get("choices")).get(0));
        Map<String, Object> message = asMap(choice.get("message"));
        assertEquals("assistant", message.get(AiPayloadFields.ROLE));
        assertEquals("Hello world", message.get(AiPayloadFields.CONTENT), "多个文本块应拼接");
        assertEquals("stop", choice.get("finish_reason"));

        Map<String, Object> usage = asMap(result.get(AiPayloadFields.USAGE));
        assertEquals(12L, ((Number) usage.get(AiPayloadFields.PROMPT_TOKENS)).longValue(), "input_tokens → prompt_tokens");
        assertEquals(7L, ((Number) usage.get(AiPayloadFields.COMPLETION_TOKENS)).longValue(), "output_tokens → completion_tokens");
        assertEquals(19L, ((Number) usage.get(AiPayloadFields.TOTAL_TOKENS)).longValue(), "缺 total 时按两者之和补齐");
        assertEquals(3L, ((Number) usage.get("cache_read_input_tokens")).longValue(), "缓存读独立携带");
    }

    @Test
    void shouldMapAnthropicStopReasonToOpenAiFinishReason() {
        assertEquals("length", finishReasonOf("max_tokens"));
        assertEquals("tool_calls", finishReasonOf("tool_use"));
        assertEquals("content_filter", finishReasonOf("refusal"));
        assertEquals("stop", finishReasonOf("pause_turn"));
        assertEquals("stop", finishReasonOf("something_unknown"), "未知原因不猜，按正常结束");
    }

    @Test
    void shouldPassThroughNonMessagesResponses() {
        String error = "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}";
        Map<String, Object> body = Map.of();
        assertEquals(error, adapter.handleResponse(error, context(ModelApiProtocolEnum.CHAT_COMPLETIONS, body)),
                "错误体必须原样透传，绝不改写成成功形状");
        assertEquals("not-json", adapter.handleResponse("not-json", context(ModelApiProtocolEnum.CHAT_COMPLETIONS, body)));
        assertEquals("", adapter.handleResponse("", context(ModelApiProtocolEnum.CHAT_COMPLETIONS, body)));
    }

    @Test
    @DisplayName("工具字段随请求换形：tools 去包装、tool_choice 换档、tool_calls 折块")
    void shouldConvertToolFieldsToAnthropicShape() {
        Map<String, Object> source = chatCompletionsBody();
        source.put(AiPayloadFields.TOOLS, List.of(Map.of(
                AiPayloadFields.TYPE, AiPayloadFields.FUNCTION,
                AiPayloadFields.FUNCTION, Map.of(
                        AiPayloadFields.NAME, "get_weather",
                        AiPayloadFields.PARAMETERS, Map.of("type", "object")))));
        source.put(AiPayloadFields.TOOL_CHOICE, "required");
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, AiPayloadFields.USER, AiPayloadFields.CONTENT, "杭州天气"),
                Map.of(AiPayloadFields.ROLE, "assistant", AiPayloadFields.TOOL_CALLS, List.of(Map.of(
                        AiPayloadFields.ID, "call_1",
                        AiPayloadFields.TYPE, AiPayloadFields.FUNCTION,
                        AiPayloadFields.FUNCTION, Map.of(
                                AiPayloadFields.NAME, "get_weather",
                                AiPayloadFields.ARGUMENTS, "{\"city\":\"杭州\"}")))),
                Map.of(AiPayloadFields.ROLE, AiPayloadFields.TOOL,
                        AiPayloadFields.TOOL_CALL_ID, "call_1", AiPayloadFields.CONTENT, "晴"))));

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.CHAT_COMPLETIONS, source)));

        Map<String, Object> tool = asMap(((List<?>) result.get(AiPayloadFields.TOOLS)).get(0));
        assertEquals("get_weather", tool.get(AiPayloadFields.NAME), "Anthropic 的 tools 没有 function 包装");
        assertFalse(tool.containsKey(AiPayloadFields.FUNCTION));
        assertEquals(Map.of("type", "object"), tool.get(AiPayloadFields.INPUT_SCHEMA));
        assertEquals(Map.of(AiPayloadFields.TYPE, "any"), result.get(AiPayloadFields.TOOL_CHOICE),
                "OpenAI 的 required 对应 Anthropic 的 any");

        List<?> messages = (List<?>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(3, messages.size(), "user + assistant + tool_result");
        Map<String, Object> assistant = asMap(messages.get(1));
        assertFalse(assistant.containsKey(AiPayloadFields.TOOL_CALLS), "OpenAI 载体字段必须移除");
        Map<String, Object> toolUse = asMap(((List<?>) assistant.get(AiPayloadFields.CONTENT)).get(0));
        assertEquals(AiPayloadFields.TOOL_USE, toolUse.get(AiPayloadFields.TYPE));
        assertEquals(Map.of("city", "杭州"), toolUse.get(AiPayloadFields.INPUT));

        Map<String, Object> toolResult = asMap(messages.get(2));
        assertEquals("user", toolResult.get(AiPayloadFields.ROLE), "tool 结果必须换成 user 角色");
        Map<String, Object> resultBlock = asMap(((List<?>) toolResult.get(AiPayloadFields.CONTENT)).get(0));
        assertEquals(AiPayloadFields.TOOL_RESULT, resultBlock.get(AiPayloadFields.TYPE));
        assertEquals("call_1", resultBlock.get(AiPayloadFields.TOOL_USE_ID));
    }

    @Test
    @DisplayName("流式走 chat chunk 转换器（非默认透传）")
    void shouldProvideChatChunkStreamAdapter() {
        StreamLineAdapter streamAdapter = adapter.streamAdapter(
                context(ModelApiProtocolEnum.CHAT_COMPLETIONS, Map.of()));

        assertNotSame(StreamLineAdapter.PASSTHROUGH, streamAdapter, "协议转换类适配器必须覆写流式适配");
        assertEquals(List.of(), streamAdapter.adapt("event: message_start"), "event 行应被抑制");
        assertEquals(List.of("data: [DONE]"), streamAdapter.adapt("data: {\"type\":\"message_stop\"}"),
                "message_stop 应合成 [DONE]");
    }

    private String finishReasonOf(String stopReason) {
        Map<String, Object> result = readMap(adapter.handleResponse(messagesResponse(stopReason),
                context(ModelApiProtocolEnum.CHAT_COMPLETIONS, Map.of())));
        return (String) asMap(((List<?>) result.get("choices")).get(0)).get("finish_reason");
    }

    private String messagesResponse(String stopReason) {
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-sonnet-4\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"Hello\"},{\"type\":\"text\",\"text\":\" world\"}],"
                + "\"stop_reason\":\"" + stopReason + "\","
                + "\"usage\":{\"input_tokens\":12,\"output_tokens\":7,\"cache_read_input_tokens\":3}}";
    }

    private Map<String, Object> readMap(String json) {
        return JsonKit.fromJson(json, new TypeReference<>() {
        });
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
