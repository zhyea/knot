package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Responses → Converse 适配器的入协议转换用例（公共用例见
 * {@link AwsBedrockConverseUpstreamAdapterTest}）。
 */
class AwsBedrockResponsesToConverseUpstreamAdapterTest extends AwsBedrockConverseUpstreamAdapterTest {

    private final AwsBedrockResponsesToConverseUpstreamAdapter adapter =
            new AwsBedrockResponsesToConverseUpstreamAdapter();

    @Override
    UpstreamAdapter adapter() {
        return adapter;
    }

    @Override
    ModelApiProtocolEnum protocol() {
        return ModelApiProtocolEnum.RESPONSES;
    }

    @Test
    void shouldExposeStableCodeAndLabel() {
        assertEquals("AWS_BEDROCK_RESPONSES_TO_CONVERSE", adapter.code());
        assertEquals("AWS Bedrock Responses 转 Converse", adapter.label());
        assertEquals(1, adapter.protocol().size(), "一适配器一协议");
    }

    @Test
    void shouldConvertResponsesToConverse() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.INPUT, "hi");
        source.put(AiPayloadFields.INSTRUCTIONS, "you are helpful");
        source.put(AiPayloadFields.MAX_OUTPUT_TOKENS, 512);
        source.put("temperature", 0.7);
        source.put("store", false);

        Map<String, Object> body = asMap(adapter.buildRequestBody(
                new UpstreamRequestContext(ModelApiProtocolEnum.RESPONSES, source, null, null, null, null, null, null, null)));

        List<?> system = (List<?>) body.get("system");
        assertEquals(1, system.size(), "instructions 应折进 system[]");
        assertEquals("you are helpful", cast(system.get(0)).get("text"));

        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size(), "input 字符串折成单条消息");
        Map<String, Object> first = cast(messages.get(0));
        assertEquals("user", first.get(AiPayloadFields.ROLE));
        assertEquals("hi", cast(((List<?>) first.get(AiPayloadFields.CONTENT)).get(0)).get("text"));

        Map<String, Object> inference = cast(body.get("inferenceConfig"));
        assertEquals(512, inference.get("maxTokens"), "max_output_tokens 经别名吸收");
        assertFalse(body.containsKey(AiPayloadFields.INPUT));
        assertFalse(body.containsKey("store"));
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
