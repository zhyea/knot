package org.chobit.knot.gateway.plugin;

public record PluginBindingView(Long bindingId,
                                String instanceCode,
                                String instanceName,
                                String packageCode,
                                String packageName,
                                String capabilityCode,
                                PluginExtensionPoint extensionPoint,
                                PluginStageCode stageCode,
                                PluginScopeType scopeType,
                                Long scopeRefId,
                                Integer orderNo,
                                String configJson,
                                Integer timeoutMs) {
}
