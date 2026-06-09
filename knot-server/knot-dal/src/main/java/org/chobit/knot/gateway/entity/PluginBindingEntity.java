package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class PluginBindingEntity {
    private Long id;
    private Long instanceId;
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
    private String status;
    private String configJson;
    private String failMode;
    private Integer timeoutMs;
}
