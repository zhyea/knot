package org.chobit.knot.gateway.adapter.request;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.AiPayloadFields;
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
 * Adapter 透传优先原则的回归测试（清单 §2.1）。
 *
 * <p>盯五件事：① 兼容路径原样透传且未知字段保留；② {@code buildRequestBody} 返回后
 * 原始请求体不被原地改写；③ 转换路径只产生协议要求的差异；④ 已有字段不被别名转换覆盖；
 * ⑤ null / 空值按既有语义处理。
 */
class OpenAiCompatibleRequestAdapterTest {

    private final OpenAiCompatibleRequestAdapter adapter = new OpenAiCompatibleRequestAdapter();

    @Test
    void shouldPassThroughCompatibleRequestKeepingUnknownFields() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MESSAGES, List.of(Map.of(AiPayloadFields.ROLE, "user", "content", "hi")));
        source.put("vendor_only_field", "keep-me");

        UpstreamRequestContext ctx = context(ModelApiProtocolEnum.CHAT_COMPLETIONS, source);
        Object result = adapter.buildRequestBody(ctx);

        // 兼容路径直接返回 context 持有的请求体，不重新构造（context 自身已做防御性复制）
        assertSame(ctx.requestBody(), result, "兼容路径应直接返回请求体本身，不重建");
        assertEquals(source, result, "透传结果应与原始请求内容一致");
        assertTrue(asMap(result).containsKey("vendor_only_field"));
    }

    @Test
    void shouldNotMutateOriginalRequestBody() {
        Map<String, Object> source = anthropicStyleBody();

        adapter.buildRequestBody(context(ModelApiProtocolEnum.MESSAGES, source));

        // 转换结果可以变化，但用户请求不能被原地改写
        assertTrue(source.containsKey(AiPayloadFields.SYSTEM), "原始请求仍应保留 system");
        assertTrue(source.containsKey(AiPayloadFields.STOP_SEQUENCES), "原始请求仍应保留 stop_sequences");
        assertFalse(source.containsKey(AiPayloadFields.STOP), "原始请求不应被写入目标协议的 stop");
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
