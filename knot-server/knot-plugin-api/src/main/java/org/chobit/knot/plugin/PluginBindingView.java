package org.chobit.knot.plugin;

/**
 * 分发器视角下的插件绑定视图：一次分发需要知道的全部信息。
 *
 * <p>2026-10-25 删除了 {@code packageCode} / {@code packageName}——「插件包」这一层分组
 * 随单表收口后已无任何读取方（插件包 / 能力 / 实例三级压成两级），留着只是让每个
 * 下游 record 多两个永远为 null 或永远重复的字段。</p>
 */
public record PluginBindingView(Long bindingId,
                                String instanceCode,
                                String instanceName,
                                String capabilityCode,
                                PluginExtensionPoint extensionPoint,
                                PluginStageCode stageCode,
                                Integer orderNo,
                                Integer timeoutMs) {
}
