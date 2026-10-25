package org.chobit.knot.plugin;

public record PluginDescriptor(String capabilityCode,
                               PluginExtensionPoint extensionPoint,
                               PluginStageCode stageCode) {
}
