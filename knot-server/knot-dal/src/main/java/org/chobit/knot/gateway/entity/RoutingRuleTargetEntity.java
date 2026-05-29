package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleTargetEntity {
    private Long id;
    private Long ruleId;
    private String targetType;
    private Long targetId;
    private Integer priority;
    private Boolean primary;
    private String targetCode;
    private String targetName;
    private String modelType;
    private Long providerId;
}
