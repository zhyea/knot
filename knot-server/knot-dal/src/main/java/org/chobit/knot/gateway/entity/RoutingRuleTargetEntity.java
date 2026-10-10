package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleTargetEntity {
    private Long id;
    /** 路由规则业务码（kb_routing_rules.rule_code），非主键 id，落库列 */
    private String ruleCode;
    private String targetType;
    private Long targetId;
    private Integer priority;
    private Boolean primary;
    private String targetCode;
    private String targetName;
    private String modelType;
    private String providerAccountCode;
}
