package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Messages → Converse 适配器的入协议转换用例（公共用例见
 * {@link AwsBedrockConverseUpstreamAdapterTest}）。
 */
class AwsBedrockMessagesToConverseUpstreamAdapterTest extends AwsBedrockConverseUpstreamAdapterTest {

    private final AwsBedrockMessagesToConverseUpstreamAdapter adapter =
            new AwsBedrockMessagesToConverseUpstreamAdapter();

    @Override
    UpstreamAdapter adapter() {
        return adapter;
    }

    @Override
    ModelApiProtocolEnum protocol() {
        return ModelApiProtocolEnum.MESSAGES;
    }

    @Test
    void shouldExposeStableCodeAndLabel() {
        assertEquals("AWS_BEDROCK_MESSAGES_TO_CONVERSE", adapter.code());
        assertEquals("AWS Bedrock Messages 转 Converse", adapter.label());
        assertEquals(1, adapter.protocol().size(), "一适配器一协议");
    }

    @Test
    void shouldConvertMessagesToConverse() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.SYSTEM, "you are helpful");
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, "user",
                        AiPayloadFields.CONTENT, List.of(Map.of("text", "hi"))))));
        source.put(AiPayloadFields.MAX_TOKENS, 512);

        Map<String, Object> body = asMap(adapter.buildRequestBody(
                new UpstreamRequestContext(ModelApiProtocolEnum.MESSAGES, source, null, null, null, null, null, null, null)));

        List<?> system = (List<?>) body.get("system");
        assertEquals(1, system.size(), "顶层 system 应折进 system[]");
        assertEquals("you are helpful", cast(system.get(0)).get("text"));

        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size());
        Map<String, Object> first = cast(messages.get(0));
        assertEquals("user", first.get(AiPayloadFields.ROLE));
        assertEquals("hi", cast(((List<?>) first.get(AiPayloadFields.CONTENT)).get(0)).get("text"),
                "content blocks 应折成文本");

        assertEquals(512, cast(body.get("inferenceConfig")).get("maxTokens"));
        assertInstanceOf(List.class, body.get(AiPayloadFields.SYSTEM),
                "顶层 system 必须是折叠后的 Converse block 数组，不能是 Anthropic 原始 system 值");
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
