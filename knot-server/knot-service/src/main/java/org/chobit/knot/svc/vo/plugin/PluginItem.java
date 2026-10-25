package org.chobit.knot.svc.vo.plugin;

public record PluginItem(Long id,
                         String code,
                         String name,
                         String capabilityCode,
                         String extensionPoint,
                         String stageCode,
                         String version,
                         Integer status,
                         Integer timeoutMs,
                         String pluginType,
                         Integer orphaned) {
}
