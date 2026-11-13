package org.chobit.knot.gateway.upstream.support;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.upstream.UpstreamPluginContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 契约测试：插件上下文的上游错误证据取值（{@link UpstreamPluginContextSupport}）。
 *
 * <p>⭐ <b>为什么解值必须在这里、不能在被插件里做</b>：{@code knot-plugin} 不能反向依赖
 * {@code knot-gateway}（会成环），插件侧根本看不到 {@link GatewayUpstreamException}。
 * 这条边界一旦被绕过（例如在 plugin 模块里加个 {@code instanceof}），
 * 编译能过但模块依赖方向就坏了——所以「网关侧解值」这一步要有测试钉住。</p>
 *
 * <p>⚠ 解不出时必须传 {@code null} 而不是 0 / 空串：超时与「上游返回 400 + 空体」
 * 是两种完全不同的故障，日志里混成一种就没法定位。</p>
 */
class UpstreamPluginContextSupportTest {

    @Test
    @DisplayName("⭐ 上游异常：状态码与错误体原文被解出并带进插件上下文")
    void carriesUpstreamErrorEvidence() {
        GatewayUpstreamException error = new GatewayUpstreamException(
                "upstream responded with status 400",
                ProxyErrorCodeEnum.UPSTREAM_ERROR.code(),
                400,
                "{\"error\":{\"message\":\"invalid model\"}}",
                null,
                "req-upstream-1",
                "https://api.example/v1/chat/completions");

        UpstreamPluginContext context = UpstreamPluginContextSupport.of(
                upstreamContext(), PluginStageCode.UPSTREAM_ERROR, null, error, "req-upstream-1",
                "https://api.example/v1/chat/completions");

        assertEquals(400, context.httpStatus(),
                "状态码丢失时日志只剩「上游报错」四个字，供应商工单无从提起");
        assertEquals("{\"error\":{\"message\":\"invalid model\"}}", context.upstreamResponseBody(),
                "错误体必须是 wire 原文：它是唯一能说明「上游到底为什么不收」的证据");
        // 同一个值的两条通路要一致，避免日志里 id 与状态码对不上号
        assertEquals("req-upstream-1", context.upstreamRequestId());
    }

    @Test
    @DisplayName("非上游异常（超时 / 连接失败）：两个字段都是 null，不是 0 或空串")
    void omitsEvidenceForNonUpstreamFailures() {
        UpstreamPluginContext context = UpstreamPluginContextSupport.of(
                upstreamContext(), PluginStageCode.UPSTREAM_ERROR, null,
                new IllegalStateException("connection reset"), null, "https://api.example/v1");

        assertNull(context.httpStatus(), "没有响应就没有状态码：写 0 会被读成「上游回了 0」");
        assertNull(context.upstreamResponseBody(), "空串与「上游返回空对象」分不开，必须 null");
    }

    @Test
    @DisplayName("成功路径与请求路径：错误证据恒为 null，不把上一阶段的残留带过来")
    void omitsEvidenceWhenNoErrorAtAll() {
        UpstreamPluginContext request = UpstreamPluginContextSupport.of(
                upstreamContext(), PluginStageCode.UPSTREAM_REQUEST, null, null, null,
                "https://api.example/v1");
        UpstreamPluginContext response = UpstreamPluginContextSupport.of(
                upstreamContext(), PluginStageCode.UPSTREAM_RESPONSE,
                Map.of("responseBody", "{}"), null, "req-1", "https://api.example/v1");

        assertNull(request.httpStatus());
        assertNull(request.upstreamResponseBody());
        assertNull(response.httpStatus());
        assertNull(response.upstreamResponseBody());
    }

    private static UpstreamRequestContext upstreamContext() {
        return new UpstreamRequestContext(
                null,
                Map.of(),
                MediaType.APPLICATION_JSON,
                null,
                null,
                null,
                null,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "trace-log-1",
                null,
                "req-own-1");
    }
}
