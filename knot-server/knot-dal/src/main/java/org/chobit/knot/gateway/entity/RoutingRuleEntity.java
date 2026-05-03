package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleEntity {
    private Long id;
    private String name;
    private String strategyType;
    private Integer priority;
    private String conditionExpr;
    private Long targetProviderId;
    private Long targetModelId;
    private String status;
}
