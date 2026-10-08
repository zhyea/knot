package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class PluginBindingEntity {
    private Long id;
    private String instanceCode;
    private String instanceName;
    private String packageCode;
    private String packageName;
    private String capabilityCode;
    private String extensionPoint;
    private String stageCode;
    private String scopeType;
    private Long scopeRefId;
    private Integer orderNo;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private String configJson;
    private Integer timeoutMs;
}
