package org.chobit.knot.gateway.upstream;

import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：{@code UPSTREAM_RESPONSE} 阶段的响应快照（{@link UpstreamProxyClient#responseSnapshot}）。
 *
 * <p>⭐ <b>两个 body 键是日志契约</b>：{@code responseBody} 是 {@code adapter.handleResponse}
 * 之后的适配结果（客户端与计费同源），{@code upstreamResponseBody} 是供应商 wire 原文。
 * 合成一个键的后果不是「日志少点信息」，而是<b>对账时拿网关重建的字段去问供应商</b> ——
 * 提工单被对方一句「我们没返回这个 id」打回。</p>
 *
 * <p>{@code streaming} 标记同样是契约：流式本来就没有整包正文，没有标记时
 * 「body=null」会被读成「日志丢了内容」。</p>
 *
 * <p>键的顺序也是契约（见 {@link #wireOriginalComesBeforeAdaptedBody}）：日志正文预算
 * 整份共享、先到先得，谁排前面谁有内容。</p>
 */
class UpstreamProxyClientTest {

    @Test
    @DisplayName("⭐ 适配后响应与 wire 原文分开命名：跨协议绑定时两者必然不同")
    void snapshotKeepsBothBodiesSeparately() {
        ProxyResult result = new ProxyResult(
                "{\"id\":\"chatcmpl-gw\",\"object\":\"chat.completion\"}",
                1L, 2L, null, null, "req-1", "https://api.anthropic.com/v1/messages",
                "{\"id\":\"msg_01\",\"type\":\"message\"}");

        Map<String, Object> snapshot = UpstreamProxyClient.responseSnapshot(result);

        assertEquals("{\"id\":\"chatcmpl-gw\",\"object\":\"chat.completion\"}",
                snapshot.get("responseBody"), "客户端与计费吃这份，字段是网关重建的");
        assertEquals("{\"id\":\"msg_01\",\"type\":\"message\"}",
                snapshot.get("upstreamResponseBody"), "对账与工单取证吃这份，必须是供应商原话");
        assertEquals(false, snapshot.get("streaming"));
    }

    @Test
    @DisplayName("流式：两个 body 都是 null，但 streaming=true 说明「本来就没有」而不是「丢了」")
    void snapshotMarksStreamingWithoutBody() {
        UpstreamStreamResponse stream = new UpstreamStreamResponse(
                Flux.empty(), MediaType.TEXT_EVENT_STREAM, null, null, null, "req-sse");
        ProxyResult result = new ProxyResult(null, 1L, 2L, null, stream, "req-sse",
                "https://api.example/v1/chat/completions", null);

        Map<String, Object> snapshot = UpstreamProxyClient.responseSnapshot(result);

        assertTrue((Boolean) snapshot.get("streaming"),
                "没有这个标记，运维会去查「上游为什么返回空 body」——其实是流式");
        assertNull(snapshot.get("responseBody"));
        assertNull(snapshot.get("upstreamResponseBody"));
    }

    @Test
    @DisplayName("⭐ 键顺序：wire 原文排在适配结果之前，且 streaming 在最前")
    void wireOriginalComesBeforeAdaptedBody() {
        ProxyResult result = new ProxyResult(
                "{\"id\":\"chatcmpl-gw\"}", 1L, 2L, null, null, "req-1",
                "https://api.example/v1/chat/completions", "{\"id\":\"msg_01\"}");

        List<String> keys = List.copyOf(
                UpstreamProxyClient.responseSnapshot(result).keySet());

        assertTrue(keys.indexOf("upstreamResponseBody") < keys.indexOf("responseBody"),
                "正文预算整份共享、先到先得（PluginLogSupport 512）：原文不可再生，"
                        + "适配结果在 gateway-response-log 另有一份，所以原文必须排前面。实际顺序：" + keys);
        assertEquals(2, keys.indexOf("streaming"),
                "streaming 排在两个 body 之前：先告诉读日志的人「本来就没有 body」，再看有没有内容。实际顺序：" + keys);
    }

    @Test
    @DisplayName("null 结果不造空快照：请求 / 异常阶段本来就没有响应可记")
    void nullResultYieldsNullSnapshot() {
        assertNull(UpstreamProxyClient.responseSnapshot(null));
    }
}
