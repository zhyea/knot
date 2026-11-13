package org.chobit.knot.gateway.upstream.support;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.upstream.UpstreamPluginContext;

import java.util.Map;

/**
 * {@link UpstreamPluginContext} 的构造原语：UPSTREAM_EXCHANGE 扩展点的两条调用链——
 * 主链路 {@code UpstreamProxyClient} 与异步任务查询 {@code AsyncTaskQueryClient}——
 * 共用同一份上下文形状，保证命中的是同一批插件绑定（分发器只按扩展点 + 阶段 +
 * 能力码 + contextType 匹配，不看 provider / model / rule）。
 *
 * <p>只负责「把 {@link UpstreamRequestContext} 投影成插件上下文」，不做任何分发；
 * provider / model / binding / protocol 均可能为 null（异步查询没有绑定），逐字段判空。</p>
 */
public final class UpstreamPluginContextSupport {

    private UpstreamPluginContextSupport() {
    }

    public static UpstreamPluginContext of(UpstreamRequestContext context,
                                           PluginStageCode stageCode,
                                           Map<String, Object> responseSnapshot,
                                           Throwable error,
                                           String upstreamRequestId,
                                           String upstreamUrl) {
        // ⭐ 上游错误证据在这里解出，而不是让插件自己去 instanceof：
        // knot-plugin 不能反向依赖 knot-gateway，插件侧看不到 GatewayUpstreamException。
        // 解不出（超时 / 连接失败 / 非上游异常）时两字段均为 null，插件按「上游没给响应」处理。
        GatewayUpstreamException upstream = error instanceof GatewayUpstreamException ex ? ex : null;
        return new UpstreamPluginContext(
                context.traceId(),
                context.traceparent(),
                stageCode,
                context.provider() == null ? null : context.provider().id(),
                context.provider() == null ? null : context.provider().code(),
                context.model() == null ? null : context.model().id(),
                context.model() == null ? null : context.model().modelCode(),
                context.protocol() == null ? null : context.protocol().code(),
                context.binding() == null ? null : context.binding().protocol(),
                context.requestBody(),
                context.contentType(),
                responseSnapshot,
                error,
                upstreamRequestId,
                upstreamUrl,
                upstream == null ? null : upstream.httpStatus(),
                upstream == null ? null : upstream.responseBody()
        );
    }
}
