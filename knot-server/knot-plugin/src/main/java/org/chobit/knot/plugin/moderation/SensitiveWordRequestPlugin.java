package org.chobit.knot.plugin.moderation;

import org.chobit.knot.plugin.PluginBindingView;
import org.chobit.knot.plugin.PluginDescriptor;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginHandler;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.gateway.GatewayPluginContext;
import org.springframework.stereotype.Component;

/**
 * 敏感词入站检测插件（同步）。
 *
 * 挂在 GATEWAY_EXCHANGE / GATEWAY_REQUEST 阶段（{@code proxy} 之前），被拦请求不消耗 RPM 事前计数与额度。
 * BLOCK 动作抛 {@link ModerationRejectException} 打断链路，由网关转 400（不转发上游、不计费）；FLAG 动作只落审计事件。
 */
@Component
public class SensitiveWordRequestPlugin implements PluginHandler<GatewayPluginContext> {

    private final ModerationGuard guard;

    public SensitiveWordRequestPlugin(ModerationGuard guard) {
        this.guard = guard;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "sensitive-word-request-check",
                "敏感词入站检测插件",
                "sensitive-word-moderation",
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                PluginStageCode.GATEWAY_REQUEST,
                200
        );
    }

    @Override
    public Class<GatewayPluginContext> contextType() {
        return GatewayPluginContext.class;
    }

    @Override
    public void handle(PluginBindingView binding, GatewayPluginContext context) {
        guard.checkRequest(context);
    }
}
