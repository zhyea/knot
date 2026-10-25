package org.chobit.knot.plugin;

public record PluginBindingView(Long bindingId,
                                String instanceCode,
                                String instanceName,
                                String packageCode,
                                String packageName,
                                String capabilityCode,
                                PluginExtensionPoint extensionPoint,
                                PluginStageCode stageCode,
                                Integer orderNo,
                                Integer timeoutMs) {
}
