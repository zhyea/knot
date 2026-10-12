package org.chobit.knot.gateway.plugin;

/**
 * 插件契约标记接口：插件系统的两类扩展点在此统一命名，各自通过具体子接口固定其执行类型
 * （{@link PluginTypeEnum}），使「同步 / 异步」的区分在接口层（而非仅数据列）就完成定义。
 *
 * <ul>
 *   <li>{@link PluginHandler} 实现为 <b>同步插件（SYNC）</b>：嵌入请求链路内联执行，可观测/改写请求与响应。</li>
 *   <li>{@link PluginEventSink} 实现为 <b>异步插件（ASYNC）</b>：离链消费 {@link PluginEvent}，不进入请求关键路径。</li>
 * </ul>
 *
 * 与 {@code kb_plugin_instances.plugin_type} 口径一致：同一套 {@link PluginTypeEnum} 作唯一权威，
 * 接口层枚举值 == 数据层落库值 == 前端 /api/common/enums 下发值。
 */
public interface PluginContract {

    /**
     * 插件执行类型。具体子接口以固定值实现，运行期不变。
     */
    PluginTypeEnum pluginType();
}
