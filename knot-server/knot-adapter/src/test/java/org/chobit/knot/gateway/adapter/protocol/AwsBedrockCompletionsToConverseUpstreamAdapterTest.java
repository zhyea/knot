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

/**
 * Completions → Converse 适配器的入协议转换用例（公共用例见
 * {@link AwsBedrockConverseUpstreamAdapterTest}）。
 */
class AwsBedrockCompletionsToConverseUpstreamAdapterTest extends AwsBedrockConverseUpstreamAdapterTest {

    private final AwsBedrockCompletionsToConverseUpstreamAdapter adapter =
            new AwsBedrockCompletionsToConverseUpstreamAdapter();

    @Override
    UpstreamAdapter adapter() {
        return adapter;
    }

    @Override
    ModelApiProtocolEnum protocol() {
        return ModelApiProtocolEnum.COMPLETIONS;
    }

    @Test
    void shouldExposeStableCodeAndLabel() {
        assertEquals("AWS_BEDROCK_COMPLETIONS_TO_CONVERSE", adapter.code());
        assertEquals("AWS Bedrock Completions 转 Converse", adapter.label());
        assertEquals(1, adapter.protocol().size(), "一适配器一协议");
    }

    @Test
    void shouldConvertCompletionsToConverse() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.PROMPT, "hi");
        source.put("max_tokens", 512);
        source.put("logprobs", 5);

        Map<String, Object> body = asMap(adapter.buildRequestBody(
                new UpstreamRequestContext(ModelApiProtocolEnum.COMPLETIONS, source, null, null, null, null, null, null, null)));

        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size(), "prompt 折成单条消息");
        Map<String, Object> first = cast(messages.get(0));
        assertEquals("user", first.get(AiPayloadFields.ROLE));
        assertEquals("hi", cast(((List<?>) first.get(AiPayloadFields.CONTENT)).get(0)).get("text"));

        assertEquals(512, cast(body.get("inferenceConfig")).get("maxTokens"));
        assertFalse(body.containsKey(AiPayloadFields.PROMPT));
        assertFalse(body.containsKey("logprobs"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
