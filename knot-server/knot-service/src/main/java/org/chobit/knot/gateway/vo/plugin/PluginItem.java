package org.chobit.knot.gateway.vo.plugin;

public record PluginItem(Long id,
                         String code,
                         String name,
                         String packageCode,
                         String packageName,
                         String capabilityCode,
                         String extensionPoint,
                         String stageCode,
                         String version,
                         String status,
                         String failMode,
                         Integer timeoutMs,
                         String configJson) {
}
