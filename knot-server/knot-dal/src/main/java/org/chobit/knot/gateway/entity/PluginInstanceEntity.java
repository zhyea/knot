package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class PluginInstanceEntity {
    private Long id;
    private String code;
    private String name;
    private String packageCode;
    private String packageName;
    private String capabilityCode;
    private String extensionPoint;
    private String stageCode;
    private String version;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private Integer timeoutMs;
    private String configJson;
}
