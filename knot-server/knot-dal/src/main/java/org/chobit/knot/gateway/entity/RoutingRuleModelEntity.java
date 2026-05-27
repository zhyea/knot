package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleModelEntity {
    private Long id;
    private Long ruleId;
    private Long modelId;
    private Integer priority;
    private Boolean primary;
    private String modelCode;
    private String modelName;
    private Long providerId;
}
