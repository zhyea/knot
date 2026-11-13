package org.chobit.knot.plugin;

/**
 * 分发器视角下的插件绑定视图：一次分发需要知道的全部信息。
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
