package org.chobit.knot.gateway.plugin;

public record PluginDescriptor(String capabilityCode,
                               PluginExtensionPoint extensionPoint,
                               PluginStageCode stageCode) {
}
