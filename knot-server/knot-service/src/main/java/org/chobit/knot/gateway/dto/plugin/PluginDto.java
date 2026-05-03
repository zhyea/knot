package org.chobit.knot.gateway.dto.plugin;

public record PluginDto(Long id, String code, String name, String pluginType, String version, String status) {
}
