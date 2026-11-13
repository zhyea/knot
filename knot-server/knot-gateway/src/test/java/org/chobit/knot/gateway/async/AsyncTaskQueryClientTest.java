package org.chobit.knot.gateway.async;

import org.chobit.knot.adapter.upstream.UpstreamAccess;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.plugin.PluginDispatchContext;
import org.chobit.knot.plugin.PluginDispatcher;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.upstream.UpstreamPluginContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步任务查询接入 UPSTREAM_* 日志插件的<b>行为契约</b>。
 *
 * <p>编排逻辑抽在包级可见的 {@link AsyncTaskQueryPluginDispatcher}：真正的查询动作以
 * {@code Supplier} 注入，于是「三阶段 dispatch + fail-open」可以在不 mock 整条 WebClient
 * 链的前提下被直接验证（项目既有教训：编排测试用子类覆写 + 调用记录，不用 Mockito）。</p>
 *
 * <p>判据不是「方法被调用」，而是<b>查询结果与异常语义没被插件影响</b>：
 * body 用 {@code assertSame} 断言同一个引用（原文未被改写），异常用 {@code assertSame}
 * 断言还是原来那个对象（未被包装 / 顶替）。</p>
 */
class AsyncTaskQueryClientTest {

    private static final String URL = "https://upstream.example.com/v1/tasks/task-1";

    @Test
    @DisplayName("成功：记录 [UPSTREAM_REQUEST, UPSTREAM_RESPONSE]，body 原文原样返回")
    void successDispatchesRequestAndResponseAndReturnsRawBody() {
        RecordingPluginDispatcher dispatcher = new RecordingPluginDispatcher();
        String raw = "{\"status\":\"succeeded\",\"output\":{\"a\":1}}";

        String result = AsyncTaskQueryPluginDispatcher.execute(
                dispatcher, upstreamContext(), URL, "task-1", () -> raw);

        assertSame(raw, result, "响应体原文必须原样返回，不能被插件改写");
        assertEquals(List.of(PluginStageCode.UPSTREAM_REQUEST, PluginStageCode.UPSTREAM_RESPONSE),
                dispatcher.stages);
        assertEquals(List.of(PluginExtensionPoint.UPSTREAM_EXCHANGE, PluginExtensionPoint.UPSTREAM_EXCHANGE),
                dispatcher.points, "两阶段都走 UPSTREAM_EXCHANGE 扩展点");

        UpstreamPluginContext requestCtx = (UpstreamPluginContext) dispatcher.contexts.get(0);
        assertTrue(requestCtx.responseSnapshot().isEmpty(), "请求阶段没有响应快照");
        assertNull(requestCtx.error(), "请求阶段没有异常");

        UpstreamPluginContext responseCtx = (UpstreamPluginContext) dispatcher.contexts.get(1);
        assertEquals(raw, responseCtx.responseSnapshot().get("responseBody"),
                "响应阶段快照要带查询回来的 body 原文");
        assertNull(responseCtx.error(), "响应阶段没有异常");
        assertNull(responseCtx.upstreamRequestId(), "异步查询侧没有 ProxyResult，供应商请求 id 传 null");
        assertEquals(URL, responseCtx.upstreamUrl(), "完整查询 URL 要落到日志上下文");
    }

    @Test
    @DisplayName("上游失败：记录 [UPSTREAM_REQUEST, UPSTREAM_ERROR]，原异常原样抛出")
    void upstreamFailureDispatchesErrorAndRethrowsOriginalException() {
        RecordingPluginDispatcher dispatcher = new RecordingPluginDispatcher();
        IllegalStateException upstreamEx = new IllegalStateException("async task query failed: status=503");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> AsyncTaskQueryPluginDispatcher.execute(
                        dispatcher, upstreamContext(), URL, "task-1", () -> {
                            throw upstreamEx;
                        }));

        assertSame(upstreamEx, thrown, "必须原样抛出上游异常，不能被包装或替换");
        assertEquals(List.of(PluginStageCode.UPSTREAM_REQUEST, PluginStageCode.UPSTREAM_ERROR),
                dispatcher.stages, "失败只走请求 + 异常两阶段，不能走响应阶段");

        UpstreamPluginContext errorCtx = (UpstreamPluginContext) dispatcher.contexts.get(1);
        assertSame(upstreamEx, errorCtx.error(), "error 必须是原异常对象");
        assertTrue(errorCtx.responseSnapshot().isEmpty(), "异常阶段没有响应快照");
    }

    @Test
    @DisplayName("fail-open：插件在 UPSTREAM_REQUEST 抛异常，查询仍返回正常 body")
    void pluginFailureOnRequestStageDoesNotAffectResult() {
        RecordingPluginDispatcher dispatcher = new RecordingPluginDispatcher();
        dispatcher.throwOn = PluginStageCode.UPSTREAM_REQUEST;
        String raw = "{\"status\":\"running\"}";

        String result = AsyncTaskQueryPluginDispatcher.execute(
                dispatcher, upstreamContext(), URL, "task-1", () -> raw);

        assertSame(raw, result, "请求阶段插件异常不能污染查询结果");
        assertEquals(List.of(PluginStageCode.UPSTREAM_REQUEST, PluginStageCode.UPSTREAM_RESPONSE),
                dispatcher.stages, "请求阶段失败后仍要走完响应阶段");
    }

    @Test
    @DisplayName("fail-open：插件在 UPSTREAM_RESPONSE 抛异常，查询仍返回正常 body")
    void pluginFailureOnResponseStageDoesNotAffectResult() {
        RecordingPluginDispatcher dispatcher = new RecordingPluginDispatcher();
        dispatcher.throwOn = PluginStageCode.UPSTREAM_RESPONSE;
        String raw = "{\"status\":\"succeeded\"}";

        String result = AsyncTaskQueryPluginDispatcher.execute(
                dispatcher, upstreamContext(), URL, "task-1", () -> raw);

        assertSame(raw, result, "响应阶段插件异常不能改变已取回的 body");
        assertEquals(List.of(PluginStageCode.UPSTREAM_REQUEST, PluginStageCode.UPSTREAM_RESPONSE),
                dispatcher.stages);
    }

    @Test
    @DisplayName("fail-open：插件在 UPSTREAM_ERROR 也抛异常时，仍抛出原上游异常而非插件异常")
    void pluginFailureOnErrorStageDoesNotMaskUpstreamException() {
        RecordingPluginDispatcher dispatcher = new RecordingPluginDispatcher();
        dispatcher.throwOn = PluginStageCode.UPSTREAM_ERROR;
        IllegalStateException upstreamEx = new IllegalStateException("upstream 503");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> AsyncTaskQueryPluginDispatcher.execute(
                        dispatcher, upstreamContext(), URL, "task-1", () -> {
                            throw upstreamEx;
                        }));

        assertSame(upstreamEx, thrown, "抛出的必须是原上游异常，不能被插件异常顶替");
        assertEquals(List.of(PluginStageCode.UPSTREAM_REQUEST, PluginStageCode.UPSTREAM_ERROR),
                dispatcher.stages);
    }

    @Test
    @DisplayName("空响应体（null）走响应阶段不抛 NPE：responseSnapshot 用 LinkedHashMap 而非 Map.of")
    void nullResponseBodyStillDispatchesResponse() {
        RecordingPluginDispatcher dispatcher = new RecordingPluginDispatcher();

        String result = AsyncTaskQueryPluginDispatcher.execute(
                dispatcher, upstreamContext(), URL, "task-1", () -> null);

        assertNull(result, "上游空响应按原文返回 null");
        assertEquals(List.of(PluginStageCode.UPSTREAM_REQUEST, PluginStageCode.UPSTREAM_RESPONSE),
                dispatcher.stages);
        UpstreamPluginContext responseCtx = (UpstreamPluginContext) dispatcher.contexts.get(1);
        assertTrue(responseCtx.responseSnapshot().containsKey("responseBody"),
                "responseBody 键要存在（值可为 null）");
        assertNull(responseCtx.responseSnapshot().get("responseBody"));
    }

    @Test
    @DisplayName("上游 4xx/5xx：抛带状态码与错误体的上游异常，不用文本拼装")
    void errorStatusThrowsStructuredUpstreamException() {
        AsyncTaskQueryClient client = clientReturning(HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"busy\"}");

        GatewayUpstreamException thrown = assertThrows(GatewayUpstreamException.class,
                () -> client.exchange(URL, access(), HttpMethod.GET));

        assertEquals(503, thrown.httpStatus(), "状态码要进结构化字段，插件侧才能按码筛选");
        assertEquals("{\"error\":\"busy\"}", thrown.responseBody(), "错误体原文要随异常带出");
        assertEquals(ProxyErrorCodeEnum.UPSTREAM_ERROR.code(), thrown.code());
    }

    @Test
    @DisplayName("上游错误但响应体为空：仍然抛异常，不被空 body 吞成「上游没给内容」")
    void errorStatusWithoutBodyStillThrows() {
        AsyncTaskQueryClient client = clientReturning(HttpStatus.BAD_GATEWAY, "");

        GatewayUpstreamException thrown = assertThrows(GatewayUpstreamException.class,
                () -> client.exchange(URL, access(), HttpMethod.GET));

        assertEquals(502, thrown.httpStatus());
        assertEquals("", thrown.responseBody());
    }

    // ---------- 夹具 ----------

    private static AsyncTaskQueryClient clientReturning(HttpStatus status, String body) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .build()))
                .build();
        return new AsyncTaskQueryClient(webClient, new AsyncTaskProperties(), null, null, null, null);
    }

    private static UpstreamAccess access() {
        return new UpstreamAccess("https://upstream.example.com", HttpHeaders.EMPTY);
    }

    private static UpstreamRequestContext upstreamContext() {
        return new UpstreamRequestContext(
                null,                          // protocol：异步查询未绑定协议，of() 里判空
                Map.of(),                      // requestBody：查询请求体为空
                MediaType.APPLICATION_JSON,
                null,                          // model / provider / binding：of() 里逐字段判空
                null,
                null,
                null,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "trace-async-1",
                null,
                "req-async-1");
    }

    /**
     * 记录每次 dispatch 的扩展点 / 阶段 / 上下文；{@code throwOn} 命中时抛异常模拟插件故障。
     */
    private static final class RecordingPluginDispatcher implements PluginDispatcher {

        private final List<PluginExtensionPoint> points = new ArrayList<>();
        private final List<PluginStageCode> stages = new ArrayList<>();
        private final List<PluginDispatchContext> contexts = new ArrayList<>();
        private PluginStageCode throwOn;

        @Override
        public <T extends PluginDispatchContext> void dispatch(PluginExtensionPoint extensionPoint,
                                                               PluginStageCode stageCode,
                                                               T context) {
            points.add(extensionPoint);
            stages.add(stageCode);
            contexts.add(context);
            if (throwOn == stageCode) {
                throw new IllegalStateException("plugin boom at " + stageCode);
            }
        }
    }
}
