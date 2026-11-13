package org.chobit.knot.plugin;

/**
 * 插件元数据：插件对自己的全部静态声明，也是 {@code kb_plugin_instances} 派生列的唯一来源。
 * {@code code} 实例业务码（全局唯一，落 {@code instance_code}，启动期 reconcile 校验非空、不重复、不超列宽）；{@code name} 落 {@code instance_name}。
 * {@code capabilityCode} 能力族码（允许多个插件共享）；{@code extensionPoint} / {@code stageCode} 为扩展点与执行阶段，分发器按「能力码 + 扩展点 + 阶段 + 上下文类型」匹配 handler。
 * {@code orderNo} 同一「扩展点 + 阶段」内的执行顺序，升序。
 */
public record PluginDescriptor(String code,
                               String name,
                               String capabilityCode,
                               PluginExtensionPoint extensionPoint,
                               PluginStageCode stageCode,
                               int orderNo) {
}
