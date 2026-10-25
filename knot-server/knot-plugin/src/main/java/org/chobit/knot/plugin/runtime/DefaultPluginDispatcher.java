package org.chobit.knot.plugin.runtime;

import java.util.Comparator;
import java.util.List;

import org.chobit.knot.gateway.plugin.PluginBindingProvider;
import org.chobit.knot.gateway.plugin.PluginBindingView;
import org.chobit.knot.gateway.plugin.PluginDescriptor;
import org.chobit.knot.gateway.plugin.PluginDispatchContext;
import org.chobit.knot.gateway.plugin.PluginDispatcher;
import org.chobit.knot.gateway.plugin.PluginExtensionPoint;
import org.chobit.knot.gateway.plugin.PluginHandler;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.springframework.stereotype.Component;

/**
 * 同步插件分发器：按「扩展点 + 阶段 + 能力码 + 上下文类型」四要素解析出唯一 handler，按 {@code orderNo} 升序内联执行。
 *
 * <p><b>为什么在 {@code knot-plugin} 而不在 {@code knot-plugin-api}</b>（2026-10-24）：{@code plugin-api}
 * 是契约模块，只承载 {@link PluginDispatcher} 等接口与 DTO；{@code @Component} 属运行时实现，放进去等于
 * 让契约模块拖入 Spring 容器职责，也逼所有依赖 {@code plugin-api} 的模块（如 admin）凭空多一个无用的
 * bean。迁到实现模块后，调用方仍只依赖 {@link PluginDispatcher} 接口，网关对 {@code knot-plugin} 保持
 * {@code runtime} 依赖即可——既让编译期的跨模块 import 被编译器直接拦下，也保证运行时 bean 一定在
 * classpath（见 {@code 40-plugin.md} 的反而教训）。</p>
 *
 * <p><b>不吞异常</b>：本类刻意<b>没有</b> try/catch。同步插件位于请求主链路上，handler 抛出的异常必须
 * 冒泡打断链路（敏感词拒绝、内容改写都依赖这一点）。故障隔离若要引入，只能在调用方按插件类型区分
 * FAIL_OPEN / FAIL_CLOSED，不在这里统一吞掉——统一吞掉等于把所有拒绝都洗成放行。</p>
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
