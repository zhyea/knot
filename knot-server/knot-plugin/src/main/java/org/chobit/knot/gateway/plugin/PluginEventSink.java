package org.chobit.knot.gateway.plugin;

/**
 * 异步插件契约（{@link PluginTypeEnum#ASYNC}）：离链消费 {@link PluginEvent}，不进入请求关键路径
 * （如日志落盘、Kafka 上报）。由同步插件在 {@link PluginHandler#handle} 中经
 * {@link PluginEventSink#emit} 触发，消费侧可异步/独立处理。
 */
public interface PluginEventSink extends PluginContract {

    void emit(PluginEvent event);

    @Override
    default PluginTypeEnum pluginType() {
        return PluginTypeEnum.ASYNC;
    }
}
