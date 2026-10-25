package org.chobit.knot.adapter.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.adapter.protocol.MessagesToChatCompletionsUpstreamAdapter;
import org.chobit.knot.adapter.protocol.StreamLineAdapter;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.util.JsonKit;
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

    @Test
    void shouldConvertChatCompletionResponseToMessages() {
        Map<String, Object> result = readMap(adapter.handleResponse(
                "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1700000000,\"model\":\"gpt-x\","
                        + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"},"
                        + "\"finish_reason\":\"length\"}],"
                        + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7,\"total_tokens\":19,"
                        + "\"prompt_tokens_details\":{\"cached_tokens\":3}}}",
                context(ModelApiProtocolEnum.MESSAGES, Map.of())));

        assertEquals("message", result.get("type"));
        assertEquals("assistant", result.get(AiPayloadFields.ROLE));
        assertEquals("max_tokens", result.get("stop_reason"), "length → max_tokens");
        Map<String, Object> block = asMap(((List<?>) result.get(AiPayloadFields.CONTENT)).get(0));
        assertEquals("text", block.get("type"));
        assertEquals("Hi", block.get("text"));

        Map<String, Object> usage = asMap(result.get(AiPayloadFields.USAGE));
        assertEquals(12L, ((Number) usage.get(AiPayloadFields.INPUT_TOKENS)).longValue(), "prompt_tokens → input_tokens");
        assertEquals(7L, ((Number) usage.get(AiPayloadFields.OUTPUT_TOKENS)).longValue(), "completion_tokens → output_tokens");
        assertFalse(usage.containsKey("cache_read_input_tokens"),
                "OpenAI 的 prompt_tokens 已含 cached_tokens，再带缓存键会被加回型提取器二次计入");
    }

    @Test
    void shouldPassThroughNonChatCompletionResponses() {
        String error = "{\"error\":{\"message\":\"bad\",\"type\":\"invalid_request_error\"}}";
        assertEquals(error, adapter.handleResponse(error, context(ModelApiProtocolEnum.MESSAGES, Map.of())),
                "错误体必须原样透传");
        String responsesShape = "{\"id\":\"resp_1\",\"object\":\"response\",\"output\":[]}";
        assertEquals(responsesShape, adapter.handleResponse(responsesShape, context(ModelApiProtocolEnum.MESSAGES, Map.of())),
                "非 chat.completion 形状不改写");
    }

    private Map<String, Object> readMap(String json) {
        return JsonKit.fromJson(json, new TypeReference<>() {
        });
    }

    @Test
    @DisplayName("工具语义反向换形：tools 加 function 包装、tool_use 折 tool_calls、tool_result 拆 tool 消息")
    void shouldConvertToolFieldsToOpenAiShape() {
        Map<String, Object> source = anthropicStyleBody();
        source.put(AiPayloadFields.TOOLS, List.of(Map.of(
                AiPayloadFields.NAME, "get_weather",
                AiPayloadFields.INPUT_SCHEMA, Map.of("type", "object"))));
        source.put(AiPayloadFields.TOOL_CHOICE, Map.of(AiPayloadFields.TYPE, "any"));
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, "assistant", AiPayloadFields.CONTENT, List.of(
                        Map.of(AiPayloadFields.TYPE, AiPayloadFields.TOOL_USE,
                                AiPayloadFields.ID, "toolu_1",
                                AiPayloadFields.NAME, "get_weather",
                                AiPayloadFields.INPUT, Map.of("city", "杭州")))),
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, List.of(
                        Map.of(AiPayloadFields.TYPE, AiPayloadFields.TOOL_RESULT,
                                AiPayloadFields.TOOL_USE_ID, "toolu_1",
                                AiPayloadFields.CONTENT, "晴"))))));

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.MESSAGES, source)));

        Map<String, Object> tool = asMap(((List<?>) result.get(AiPayloadFields.TOOLS)).get(0));
        assertEquals(AiPayloadFields.FUNCTION, tool.get(AiPayloadFields.TYPE));
        assertEquals(Map.of("type", "object"),
                asMap(tool.get(AiPayloadFields.FUNCTION)).get(AiPayloadFields.PARAMETERS),
                "input_schema → parameters");
        assertEquals("required", result.get(AiPayloadFields.TOOL_CHOICE), "Anthropic 的 any 对应 OpenAI 的 required");

        // system 提升成首条消息，其后是 assistant(tool_calls) 与 tool 结果
        List<?> messages = (List<?>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(3, messages.size(), "system + assistant + tool（纯 tool_result 不再留空 user）");
        Map<String, Object> assistant = asMap(messages.get(1));
        Map<String, Object> call = asMap(((List<?>) assistant.get(AiPayloadFields.TOOL_CALLS)).get(0));
        assertEquals("toolu_1", call.get(AiPayloadFields.ID));
        assertEquals("{\"city\":\"杭州\"}",
                asMap(call.get(AiPayloadFields.FUNCTION)).get(AiPayloadFields.ARGUMENTS),
                "input 对象必须序列化成 arguments 文本");

        Map<String, Object> toolMessage = asMap(messages.get(2));
        assertEquals(AiPayloadFields.TOOL, toolMessage.get(AiPayloadFields.ROLE));
        assertEquals("toolu_1", toolMessage.get(AiPayloadFields.TOOL_CALL_ID));
    }

    @Test
    @DisplayName("流式走 Messages 事件转换器（合成 event 行与收尾事件）")
    void shouldProvideMessagesEventStreamAdapter() {
        StreamLineAdapter streamAdapter = adapter.streamAdapter(
                context(ModelApiProtocolEnum.MESSAGES, Map.of()));

        assertNotSame(StreamLineAdapter.PASSTHROUGH, streamAdapter);
        assertTrue(streamAdapter.adapt("data: [DONE]").contains("event: message_stop"),
                "OpenAI 的 [DONE] 必须换成品类收尾事件");
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
