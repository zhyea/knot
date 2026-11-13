package org.chobit.knot.gateway.upstream;

import org.chobit.knot.adapter.auth.BearerAuthApplier;
import org.chobit.knot.adapter.auth.UpstreamAuthApplierCatalog;
import org.chobit.knot.adapter.model.ref.ModelRef;
import org.chobit.knot.adapter.model.ref.ProviderAccountRef;
import org.chobit.knot.adapter.protocol.PassthroughUpstreamAdapter;
import org.chobit.knot.adapter.protocol.UpstreamAdapter;
import org.chobit.knot.adapter.protocol.UpstreamAdapterCatalog;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.upstream.protocol.UpstreamProtocolExecutor;
import org.chobit.knot.gateway.upstream.protocol.UpstreamProtocolExecutorRegistry;
import org.chobit.knot.gateway.upstream.request.UpstreamAdapterRegistry;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.chobit.knot.plugin.PluginStageCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 上游响应日志的<b>分发时序</b>接线测试。
 *
 * <p>⭐ 流式在 {@code proxy()} 返回时只有响应头、一个字节正文都没有，此刻发
 * {@code UPSTREAM_RESPONSE} 只会得到一条 {@code body=null} 的空记录；所以流式跳过、
 * 改由流结束的 {@code doFinally} 补记一条带 tee 快照的。非流式必须保持原样（请求 + 响应两条）。
 * 只测 tee 缓冲内容证明不了「这条日志到底发没发、发了几次」，故这里直接驱动分发点。</p>
 */
class UpstreamProxyClientDispatchTest {

    private static final String WIRE_TAIL = "data: {\"usage\":{\"input_tokens\":999}}\ndata: [DONE]\n";

    @Test
    @DisplayName("⭐ 流式：proxy 阶段只发请求日志，流结束补记一条带 wire 原文的响应日志")
    void streamingResponseIsDeferredUntilTail() {
        UpstreamStreamResponse stream = new UpstreamStreamResponse(
                Flux.empty(), MediaType.TEXT_EVENT_STREAM, null, null, null, null);
        ProxyResult streaming = new ProxyResult(null, 1L, 2L, null, stream, null,
                "https://api.example.com/v1/chat/completions", null);
        RecordingClient client = new RecordingClient(streaming);
        UpstreamRequestContext context = context();

        ProxyResult result = client.proxy(context);

        assertEquals(List.of(PluginStageCode.UPSTREAM_REQUEST), client.stages,
                "开始前那条不含任何正文，再发一条只是同形噪声；实际：" + client.stages);

        client.logStreamTail(context, result, WIRE_TAIL);

        assertEquals(List.of(PluginStageCode.UPSTREAM_REQUEST, PluginStageCode.UPSTREAM_RESPONSE),
                client.stages, "流结束补记一条，且只一条");
        Map<String, Object> snapshot = client.snapshots.get(1);
        assertEquals(WIRE_TAIL, snapshot.get("upstreamResponseBody"),
                "补记结果要复用 upstreamResponseBody 这个键，插件侧不需要为流式分叉");
        assertEquals(Boolean.TRUE, snapshot.get("streaming"), "流式标记不变");
        assertNull(snapshot.get("responseBody"), "流式没有整包适配后正文");
    }

    @Test
    @DisplayName("非流式：请求 + 响应两条日志原样保留，不受流式延后影响")
    void bufferedResponseKeepsBothStages() {
        ProxyResult buffered = new ProxyResult("{\"id\":\"chatcmpl-1\"}", 1L, 2L, null);
        RecordingClient client = new RecordingClient(buffered);

        client.proxy(context());

        assertEquals(List.of(PluginStageCode.UPSTREAM_REQUEST, PluginStageCode.UPSTREAM_RESPONSE),
                client.stages, "整包路径本来就有正文，不该被延后");
        assertEquals("{\"id\":\"chatcmpl-1\"}", client.snapshots.get(1).get("responseBody"));
    }

    private static UpstreamRequestContext context() {
        return new UpstreamRequestContext(
                ModelApiProtocolEnum.CHAT_COMPLETIONS, Map.of(), MediaType.APPLICATION_JSON,
                new ModelRef(1L, "gpt-4o", "gpt-4o", null, null, null),
                new ProviderAccountRef(2L, "ACC", "https://api.example.com", "OPENAI"),
                Map.of(), null, null, "trace-1", null, "req-1");
    }

    /**
     * 只替换分发点，其余全用真实目录 / 透传适配器：接线测试要证明的是「发不发、发几次」，
     * 不是把整条代理链路也替身掉。{@code pluginDispatcher} 传 null 正是因为分发点已被覆写，
     * 真实分发器在这条路径上一定不会被调用。
     */
    private static final class RecordingClient extends UpstreamProxyClient {

        private final List<PluginStageCode> stages = new ArrayList<>();
        private final List<Map<String, Object>> snapshots = new ArrayList<>();

        RecordingClient(ProxyResult result) {
            super(null,
                    new UpstreamProtocolExecutorRegistry(List.of(executorReturning(result))),
                    new UpstreamAdapterRegistry(
                            new UpstreamAdapterCatalog(List.of(new PassthroughUpstreamAdapter()))),
                    null,
                    null,
                    new UpstreamAuthApplierCatalog(List.of(new BearerAuthApplier())));
        }

        @Override
        void dispatchPlugin(UpstreamRequestContext context,
                            PluginStageCode stageCode,
                            ProxyResult result,
                            Throwable error,
                            String upstreamUrl) {
            stages.add(stageCode);
            snapshots.add(responseSnapshot(result));
        }
    }

    private static UpstreamProtocolExecutor executorReturning(ProxyResult result) {
        return new UpstreamProtocolExecutor() {
            @Override
            public boolean supports(ModelApiProtocolEnum protocol) {
                return true;
            }

            @Override
            public ProxyResult execute(UpstreamRequestContext context, UpstreamAdapter adapter) {
                return result;
            }
        };
    }
}
