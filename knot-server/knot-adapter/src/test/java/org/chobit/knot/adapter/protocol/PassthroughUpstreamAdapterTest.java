package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.protocol.PassthroughUpstreamAdapter;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 透传适配器回归测试：盯三件事 —— ① {@code buildRequestBody} 原样返回请求体且不做任何转换；
 * ② code/label 稳定；③ 全协议声明（透传是唯一的多协议适配器）。鉴权与本适配器无关
 * （由供应商侧 auth_applier 策略决定）。
 */
class PassthroughUpstreamAdapterTest {

    private final PassthroughUpstreamAdapter adapter = new PassthroughUpstreamAdapter();

    @Test
    void shouldExposeStableCodeAndLabel() {
        assertEquals("PASSTHROUGH", adapter.code());
        assertEquals("透传（默认）", adapter.label());
    }

    @Test
    void shouldDeclareAllProtocols() {
        assertTrue(adapter.protocol().contains(ModelApiProtocolEnum.CHAT_COMPLETIONS));
        assertTrue(adapter.protocol().contains(ModelApiProtocolEnum.CUSTOM));
        assertEquals(ModelApiProtocolEnum.values().length, adapter.protocol().size());
    }

    @Test
    void shouldReturnOriginalRequestBodyWithoutTransformation() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MESSAGES, java.util.List.of(
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "hi")));
        source.put("vendor_only_field", "keep-me");

        UpstreamRequestContext ctx = context(ModelApiProtocolEnum.CHAT_COMPLETIONS, source);
        Object result = adapter.buildRequestBody(ctx);

        assertSame(ctx.requestBody(), result, "透传应直接返回请求体本身，不重建");
        assertEquals(source, result, "透传结果应与原始请求内容一致");
        assertTrue(((Map<?, ?>) result).containsKey("vendor_only_field"));
    }

    @Test
    void shouldNotMutateOriginalRequestBody() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MESSAGES, java.util.List.of(
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "hi")));
        source.put(AiPayloadFields.MODEL, "gpt-4o");

        adapter.buildRequestBody(context(ModelApiProtocolEnum.MESSAGES, source));

        assertTrue(source.containsKey(AiPayloadFields.MODEL), "原始请求不应被改写");
        assertTrue(source.containsKey(AiPayloadFields.MESSAGES));
    }

    @Test
    @DisplayName("视频协议走兜底透传时也要剥回推地址：没配适配器的绑定最容易漏")
    void shouldStripCallbackFieldsForVideoProtocol() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("webhook_url", "https://client.example/hook2");
        options.put("mode", "pro");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("prompt", "a cat");
        source.put("callback_url", "https://client.example/hook");
        source.put("options", options);

        UpstreamRequestContext ctx = context(ModelApiProtocolEnum.VIDEO_GENERATIONS, source);
        Map<?, ?> sent = (Map<?, ?>) adapter.buildRequestBody(ctx);

        assertNotSame(ctx.requestBody(), sent, "有命中时不应返回原实例，否则等于改坏了快照");
        assertEquals(Map.of("prompt", "a cat", "options", Map.of("mode", "pro")), sent);
        assertTrue(source.containsKey("callback_url"), "原始请求不应被改写");
        assertTrue(options.containsKey("webhook_url"), "嵌套层同样不应被改写");
    }

    private UpstreamRequestContext context(ModelApiProtocolEnum protocol, Map<String, Object> body) {
        return new UpstreamRequestContext(protocol, body, null, null, null, null, null, null, null);
    }
}
