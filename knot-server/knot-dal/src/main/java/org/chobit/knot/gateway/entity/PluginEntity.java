package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class PluginEntity {
    private Long id;
    private String code;
    private String name;
    private String pluginType;
    private String version;
    private String status;
}
