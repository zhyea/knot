package org.chobit.knot.svc.dto.plugin;

public record PluginDto(Long id,
                        String code,
                        String name,
                        String packageCode,
                        String packageName,
                        String capabilityCode,
                        String extensionPoint,
                        String stageCode,
                        String version,
                        Integer status,
                        Integer timeoutMs,
                        String pluginType,
                        String configJson,
                        Integer orphaned) {
}
