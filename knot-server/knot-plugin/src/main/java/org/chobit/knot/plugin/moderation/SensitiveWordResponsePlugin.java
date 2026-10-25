package org.chobit.knot.plugin.moderation;

import org.chobit.knot.plugin.PluginBindingView;
import org.chobit.knot.plugin.PluginDescriptor;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginHandler;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.gateway.GatewayPluginContext;
import org.springframework.stereotype.Component;

/**
 * 敏感词出站检测插件（同步）。
 *
 * <p>挂在 GATEWAY_EXCHANGE / GATEWAY_RESPONSE 阶段：该阶段在用量记账之后，
 * 因此命中即 400 时<b>上游成本已产生、费用照记</b>（惩罚性计费留 M2 叠加系数）。</p>
 *
 * <p>只覆盖整包响应：流式响应体在此阶段是 {@code StreamingResponseBody}，
 * 拿不到文本故直接放行，按 2026-10-13 口径<b>不截断流</b>，避免丢失最终 usage。</p>
 */
@Component
public class SensitiveWordResponsePlugin implements PluginHandler<GatewayPluginContext> {

    private final ModerationGuard guard;

    /**
     * Constructs a new instance.
     */
    public SensitiveWordResponsePlugin(ModerationGuard guard) {
        this.guard = guard;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "sensitive-word-response-check",
                "敏感词出站检测插件",
                "sensitive-word-moderation",
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                PluginStageCode.GATEWAY_RESPONSE,
                200
        );
    }

    @Override
    public Class<GatewayPluginContext> contextType() {
        return GatewayPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, GatewayPluginContext context) {
        guard.checkResponse(context);
    }
}
