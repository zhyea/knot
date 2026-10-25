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
 * <p>挂在 GATEWAY_EXCHANGE / GATEWAY_REQUEST 阶段：该阶段在 {@code proxy} 之前，
 * 因此天然早于频控与额度检查——被拦请求不会消耗 RPM 事前计数与额度。</p>
 *
 * <p>BLOCK 动作抛 {@link ModerationRejectException}：同步插件在请求线程内联执行，
 * 异常直接冒泡打断链路，由网关异常处理器转 400（不转发上游、不计费）。
 * FLAG 动作只落审计事件，不改变链路。</p>
 */
@Component
public class SensitiveWordRequestPlugin implements PluginHandler<GatewayPluginContext> {

    private final ModerationGuard guard;

    /**
     * Constructs a new instance.
     */
    public SensitiveWordRequestPlugin(ModerationGuard guard) {
        this.guard = guard;
    }

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "sensitive-word-moderation",
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                PluginStageCode.GATEWAY_REQUEST
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
