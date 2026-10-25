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

/**
 * ChatCompletions → Converse 适配器的入协议转换用例（公共用例见
 * {@link AwsBedrockConverseUpstreamAdapterTest}）。
 */
class AwsBedrockChatCompletionsToConverseUpstreamAdapterTest extends AwsBedrockConverseUpstreamAdapterTest {

    private final AwsBedrockChatCompletionsToConverseUpstreamAdapter adapter =
            new AwsBedrockChatCompletionsToConverseUpstreamAdapter();

    @Override
    UpstreamAdapter adapter() {
        return adapter;
    }

    @Override
    ModelApiProtocolEnum protocol() {
        return ModelApiProtocolEnum.CHAT_COMPLETIONS;
    }

    @Test
    void shouldExposeStableCodeAndLabel() {
        assertEquals("AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE", adapter.code());
        assertEquals("AWS Bedrock ChatCompletions 转 Converse", adapter.label());
        assertEquals(1, adapter.protocol().size(), "一适配器一协议");
    }

    @Test
    void shouldConvertChatCompletionsToConverse() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MODEL, "gpt-4o");
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, "system", AiPayloadFields.CONTENT, "you are helpful"),
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "hi"))));
        source.put(AiPayloadFields.MAX_TOKENS, 512);
        source.put("temperature", 0.7);
        source.put(AiPayloadFields.STOP, List.of("\n\n"));

        Map<String, Object> body = asMap(adapter.buildRequestBody(
                new UpstreamRequestContext(ModelApiProtocolEnum.CHAT_COMPLETIONS, source, null, null, null, null, null, null, null)));

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
        Map<String, Object> body = asMap(adapter.buildRequestBody(
                new UpstreamRequestContext(ModelApiProtocolEnum.CHAT_COMPLETIONS,
                        Map.of(AiPayloadFields.MESSAGES, new ArrayList<>()), null, null, null, null, null, null, null)));
        assertEquals(1024, cast(body.get("inferenceConfig")).get("maxTokens"));
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
