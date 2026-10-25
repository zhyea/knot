package org.chobit.knot.adapter.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.adapter.model.ref.ModelApiBindingRef;
import org.chobit.knot.adapter.model.ref.ModelRef;
import org.chobit.knot.adapter.protocol.UpstreamAdapter;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.common.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AWS Bedrock Converse 适配器族（按入协议一适配器一协议）的共享回归测试基类。
 *
 * <p>四个适配器共用 Converse 目标形状构造、响应转换与路径推导（{@code AwsBedrockConverseSupport}），
 * 公共用例在此执行一遍；各入协议的请求体转换用例由子类补。</p>
 */
abstract class AwsBedrockConverseUpstreamAdapterTest {

    private static final String MODEL_ID = "anthropic.claude-sonnet-4-20250514-v1:0";

    /** 子类给出被测适配器实例。 */
    abstract UpstreamAdapter adapter();

    abstract ModelApiProtocolEnum protocol();

    @Test
    void shouldEncodeModelIdColonInConversePath() {
        assertEquals("/model/anthropic.claude-sonnet-4-20250514-v1%3A0/converse",
                adapter().resolvePath(contextWithModel(Map.of()), null));
    }

    @Test
    void shouldPreferBindingApiPathOverride() {
        ModelApiBindingRef binding = new ModelApiBindingRef("/model/custom-path/converse",
                null, null, null, null);
        assertEquals("/model/custom-path/converse",
                adapter().resolvePath(contextWithModel(Map.of(), binding), null));
    }

    @Test
    void shouldNotSupportStreamingInPhaseOne() {
        assertFalse(adapter().streamSupported(contextWithModel(Map.of())),
                "Converse 流式是 EventStream 二进制帧，P1 走整包");
    }

    @Test
    void shouldReturnErrorBodyUntouched() {
        // 非 Converse 响应（如 AWS 的 4xx 错误体）不能硬转成 OpenAI 形状，否则会把错误信息吞掉
        String error = "{\"message\":\"ValidationException\",\"__type\":\"ValidationException\"}";
        assertEquals(error, adapter().handleResponse(error, contextWithModel(Map.of())));
    }

    @Test
    void shouldMapStopReasonToOpenAiFinishReason() {
        assertEquals("length", finishReasonOf("max_tokens"));
        assertEquals("tool_calls", finishReasonOf("tool_use"));
        assertEquals("content_filter", finishReasonOf("guardrail_intervened"));
        assertEquals("stop", finishReasonOf("something_unknown"));
    }

    @Test
    void shouldConvertConverseResponseToOpenAiShape() {
        String converse = converseResponse();

        Map<String, Object> result = readMap(adapter().handleResponse(converse, contextWithModel(Map.of())));

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
    void shouldProduceUsageReadableByDefaultTokenExtractor() {
        // 生产链路口径：整包路径的用量提取吃的是 handleResponse 的产物，
        // 适配器转出的 OpenAI 形状必须能被通用 TokenBillingUsage 别名表读出（含缓存读写）——
        // 这是 Bedrock 绑定无需专用用量提取器、直接走 DEFAULT-TOKEN 的安全依据
        String converse = "{\"output\":{\"message\":{\"role\":\"assistant\","
                + "\"content\":[{\"text\":\"Hello\"}]}},"
                + "\"stopReason\":\"end_turn\","
                + "\"usage\":{\"inputTokens\":12,\"outputTokens\":7,\"totalTokens\":19,"
                + "\"cacheReadInputTokens\":3,\"cacheWriteInputTokens\":4}}";

        Map<String, Object> result = readMap(adapter().handleResponse(converse, contextWithModel(Map.of())));

        TokenBillingUsage billing = TokenBillingUsage.fromResponse(result);
        assertEquals(12L, billing.inputTokens());
        assertEquals(7L, billing.outputTokens());
        assertEquals(19L, billing.totalTokens());
        assertEquals(3L, billing.cacheReadTokens());
        assertEquals(4L, billing.cacheWriteTokens());
        assertNotNull(billing);
        assertFalse(billing.isEmpty());
    }

    private String converseResponse() {
        return "{\"output\":{\"message\":{\"role\":\"assistant\","
                + "\"content\":[{\"text\":\"Hello\"},{\"text\":\" world\"}]}},"
                + "\"stopReason\":\"end_turn\","
                + "\"usage\":{\"inputTokens\":12,\"outputTokens\":7,\"totalTokens\":19,"
                + "\"cacheReadInputTokens\":3,\"cacheWriteInputTokens\":0}}";
    }

    private String finishReasonOf(String stopReason) {
        String converse = "{\"output\":{\"message\":{\"content\":[{\"text\":\"x\"}]}},"
                + "\"stopReason\":\"" + stopReason + "\"}";
        Map<String, Object> result = readMap(adapter().handleResponse(converse, contextWithModel(Map.of())));
        return (String) cast(((List<?>) result.get("choices")).get(0)).get("finish_reason");
    }

    private UpstreamRequestContext contextWithModel(Map<String, Object> body) {
        return contextWithModel(body, null);
    }

    private UpstreamRequestContext contextWithModel(Map<String, Object> body, ModelApiBindingRef binding) {
        ModelRef model = new ModelRef(1L, "claude-sonnet-4", MODEL_ID, null, null, null);
        return new UpstreamRequestContext(protocol(), body, null,
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
