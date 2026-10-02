package org.chobit.knot.gateway.adapter.request;

import org.chobit.knot.gateway.adapter.AuthScheme;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 透传适配器回归测试：盯三件事 —— ① {@code buildRequestBody} 原样返回请求体且不做任何转换；
 * ② {@code authScheme} 声明为 {@link AuthScheme#NONE}（不注入鉴权头）；③ code/label 稳定。
 */
class PassthroughRequestAdapterTest {

    private final PassthroughRequestAdapter adapter = new PassthroughRequestAdapter();

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
    void shouldDeclareNoneAuthScheme() {
        assertEquals(AuthScheme.NONE, adapter.authScheme(), "纯透传适配器不应注入任何鉴权头");
    }

    private UpstreamRequestContext context(ModelApiProtocolEnum protocol, Map<String, Object> body) {
        return new UpstreamRequestContext(protocol, body, null, null, null, null, null, null, null);
    }
}
