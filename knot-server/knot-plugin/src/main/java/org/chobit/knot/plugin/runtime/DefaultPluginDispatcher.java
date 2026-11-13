package org.chobit.knot.plugin.runtime;

import java.util.Comparator;
import java.util.List;

import org.chobit.knot.plugin.PluginBindingProvider;
import org.chobit.knot.plugin.PluginBindingView;
import org.chobit.knot.plugin.PluginDescriptor;
import org.chobit.knot.plugin.PluginDispatchContext;
import org.chobit.knot.plugin.PluginDispatcher;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginHandler;
import org.chobit.knot.plugin.PluginStageCode;
import org.springframework.stereotype.Component;

/**
 * 同步插件分发器：按「扩展点 + 阶段 + 能力码 + 上下文类型」四要素解析出唯一 handler，按 {@code orderNo} 升序内联执行。
 *
 * 不吞异常：handler 抛出的异常直接冒泡打断链路（敏感词拒绝、内容改写依赖这一点）；故障隔离只能在调用方
 * 按插件类型区分 FAIL_OPEN / FAIL_CLOSED。
 */
@Component
public class DefaultPluginDispatcher implements PluginDispatcher {

    private final PluginBindingProvider bindingProvider;

    private final List<PluginHandler<?>> handlers;

    public DefaultPluginDispatcher(PluginBindingProvider bindingProvider, List<PluginHandler<?>> handlers) {
        this.bindingProvider = bindingProvider;
        this.handlers = handlers;
    }

    @Override
    public <T extends PluginDispatchContext> void dispatch(PluginExtensionPoint extensionPoint,
                                                           PluginStageCode stageCode,
                                                           T context) {
        List<PluginBindingView> bindings = bindingProvider.listBindings(extensionPoint, stageCode).stream()
            .sorted(Comparator.comparing(binding -> binding.orderNo() == null ? Integer.MAX_VALUE : binding.orderNo()))
            .toList();

        for (PluginBindingView binding : bindings) {
            PluginHandler<T> handler = resolveHandler(binding, context);
            if (handler != null) {
                handler.handle(binding, context);
            }
        }
    }

    /**
     * 四要素完全匹配才算命中；无对应 handler 的绑定会被跳过而不是报错
     * （绑定可能指向尚未打进本部署包的插件包）。
     */
    @SuppressWarnings("unchecked")
    private <T extends PluginDispatchContext> PluginHandler<T> resolveHandler(PluginBindingView binding, T context) {
        for (PluginHandler<?> candidate : handlers) {
            PluginDescriptor descriptor = candidate.descriptor();
            if (!descriptor.capabilityCode().equalsIgnoreCase(binding.capabilityCode())) {
                continue;
            }
            if (descriptor.extensionPoint() != binding.extensionPoint()) {
                continue;
            }
            if (descriptor.stageCode() != binding.stageCode()) {
                continue;
            }
            if (!candidate.contextType().isInstance(context)) {
                continue;
            }
            return (PluginHandler<T>) candidate;
        }
        return null;
    }
}
