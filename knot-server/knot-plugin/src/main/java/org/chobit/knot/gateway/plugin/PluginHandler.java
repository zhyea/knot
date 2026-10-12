package org.chobit.knot.gateway.plugin;

/**
 * 同步插件契约（{@link PluginTypeEnum#SYNC}）：嵌入网关/上游请求链路
 * （{@link PluginExtensionPoint} + {@link PluginStageCode}）内联执行，可观测/改写请求与响应。
 * 经 {@link PluginDispatcher#dispatch} 在对应阶段同步调用 {@link #handle}。
 */
public interface PluginHandler<T extends PluginDispatchContext> extends PluginContract {

    PluginDescriptor descriptor();

    Class<T> contextType();

    void handle(PluginBindingView binding, T context);

    @Override
    default PluginTypeEnum pluginType() {
        return PluginTypeEnum.SYNC;
    }
}
