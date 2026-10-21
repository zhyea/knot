package org.chobit.knot.gateway.adapter.request;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bedrock Converse 适配器回归测试：请求侧 chat→converse、响应侧 converse→OpenAI、
 * 路径里模型 ID 的 {@code :} 必须编码（否则签名覆盖的路径与实际发出的不一致）。
 */
class BedrockConverseRequestAdapterTest {

    private static final String MODEL_ID = "anthropic.claude-sonnet-4-20250514-v1:0";

    private final BedrockConverseRequestAdapter adapter = new BedrockConverseRequestAdapter();

    @Test
    void shouldEncodeModelIdColonInConversePath() {
        assertEquals("/model/anthropic.claude-sonnet-4-20250514-v1%3A0/converse",
                adapter.resolvePath(contextWithModel(Map.of()), null));
    }

    @Test
    void shouldPreferBindingApiPathOverride() {
        ModelApiBindingEntity binding = new ModelApiBindingEntity();
        binding.setApiPath("/model/custom-path/converse");
        assertEquals("/model/custom-path/converse",
                adapter.resolvePath(contextWithModel(Map.of(), binding), null));
    }

    @Test
    void shouldConvertChatCompletionsToConverse() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MODEL, "gpt-4o");
        source.put(AiPayloadFields.MESSAGES, List.of(
                Map.of(AiPayloadFields.ROLE, "system", AiPayloadFields.CONTENT, "you are helpful"),
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "hi")));
        source.put(AiPayloadFields.MAX_TOKENS, 512);
        source.put("temperature", 0.7);
        source.put(AiPayloadFields.STOP, List.of("\n\n"));

        Map<String, Object> body = cast(adapter.buildRequestBody(contextWithModel(source)));

        List<?> system = (List<?>) body.get("system");
        assertEquals(1, system.size());
        assertEquals("you are helpful", cast(system.get(0)).get("text"), "system 角色应折进 system[]");

        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size(), "system 不应留在 messages 里");
        Map<String, Object> first = cast(messages.get(0));
        assertEquals("user", first.get(AiPayloadFields.ROLE));
        assertEquals("hi", cast(((List<?>) first.get(AiPayloadFields.CONTENT)).get(0)).get("text"));

        Map<String, Object> inference = cast(body.get("inferenceConfig"));
        assertEquals(512, inference.get("maxTokens"));
        assertEquals(0.7, ((Number) inference.get("temperature")).doubleValue());
        assertEquals(List.of("\n\n"), body.get("stopSequences"));
        assertFalse(body.containsKey(AiPayloadFields.MODEL), "model 由路径承载，不进请求体");
    }

    @Test
    void shouldDefaultMaxTokensWhenNotProvided() {
        Map<String, Object> body = cast(adapter.buildRequestBody(contextWithModel(
                Map.of(AiPayloadFields.MESSAGES, List.of()))));
        assertEquals(1024, cast(body.get("inferenceConfig")).get("maxTokens"));
    }

    @Test
    void shouldConvertConverseResponseToOpenAiShape() {
        String converse = "{\"output\":{\"message\":{\"role\":\"assistant\","
                + "\"content\":[{\"text\":\"Hello\"},{\"text\":\" world\"}]}},"
                + "\"stopReason\":\"end_turn\","
                + "\"usage\":{\"inputTokens\":12,\"outputTokens\":7,\"totalTokens\":19,"
                + "\"cacheReadInputTokens\":3,\"cacheWriteInputTokens\":0}}";

        String handled = adapter.handleResponse(converse, contextWithModel(Map.of()));
        Map<String, Object> result = readMap(handled);

        assertEquals("chat.completion", result.get("object"));
        List<?> choices = (List<?>) result.get("choices");
        Map<String, Object> choice = cast(choices.get(0));
        Map<String, Object> message = cast(choice.get("message"));
        assertEquals("assistant", message.get(AiPayloadFields.ROLE));
        assertEquals("Hello world", message.get(AiPayloadFields.CONTENT), "多个文本块应拼接");
        assertEquals("stop", choice.get("finish_reason"));

        Map<String, Object> usage = cast(result.get(AiPayloadFields.USAGE));
        assertEquals(12L, ((Number) usage.get(AiPayloadFields.PROMPT_TOKENS)).longValue());
        assertEquals(7L, ((Number) usage.get(AiPayloadFields.COMPLETION_TOKENS)).longValue());
        assertEquals(19L, ((Number) usage.get(AiPayloadFields.TOTAL_TOKENS)).longValue());
        assertEquals(3L, ((Number) usage.get("cache_read_input_tokens")).longValue());
    }

    @Test
    void shouldMapStopReasonToOpenAiFinishReason() {
        assertEquals("length", finishReasonOf("max_tokens"));
        assertEquals("tool_calls", finishReasonOf("tool_use"));
        assertEquals("content_filter", finishReasonOf("guardrail_intervened"));
        assertEquals("stop", finishReasonOf("something_unknown"));
    }

    @Test
    void shouldReturnErrorBodyUntouched() {
        // 非 Converse 响应（如 AWS 的 4xx 错误体）不能硬转成 OpenAI 形状，否则会把错误信息吞掉
        String error = "{\"message\":\"ValidationException\",\"__type\":\"ValidationException\"}";
        assertEquals(error, adapter.handleResponse(error, contextWithModel(Map.of())));
    }

    @Test
    void shouldExtractCamelCaseUsage() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(AiPayloadFields.USAGE, Map.of(
                "inputTokens", 120, "outputTokens", 30, "totalTokens", 150,
                "cacheReadInputTokens", 10, "cacheWriteInputTokens", 5));

        TokenBillingUsage usage = (TokenBillingUsage) adapter.extractUsageFromBody(body);
        assertEquals(120L, usage.inputTokens());
        assertEquals(30L, usage.outputTokens());
        assertEquals(150L, usage.totalTokens());
        assertEquals(10L, usage.cacheReadTokens());
        assertEquals(5L, usage.cacheWriteTokens());
    }

    @Test
    void shouldNotSupportStreamingInPhaseOne() {
        assertFalse(adapter.streamSupported(contextWithModel(Map.of())),
                "Converse 流式是 EventStream 二进制帧，P1 走整包");
    }

    private String finishReasonOf(String stopReason) {
        String converse = "{\"output\":{\"message\":{\"content\":[{\"text\":\"x\"}]}},"
                + "\"stopReason\":\"" + stopReason + "\"}";
        Map<String, Object> result = readMap(adapter.handleResponse(converse, contextWithModel(Map.of())));
        return (String) cast(((List<?>) result.get("choices")).get(0)).get("finish_reason");
    }

    private UpstreamRequestContext contextWithModel(Map<String, Object> body) {
        return contextWithModel(body, null);
    }

    private UpstreamRequestContext contextWithModel(Map<String, Object> body, ModelApiBindingEntity binding) {
        ModelEntity model = new ModelEntity();
        model.setUpstreamModel(MODEL_ID);
        return new UpstreamRequestContext(ModelApiProtocolEnum.CHAT_COMPLETIONS, body, null,
                model, null, null, binding, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> readMap(String json) {
        return JsonKit.fromJson(json, new TypeReference<>() {
        });
    }
}
