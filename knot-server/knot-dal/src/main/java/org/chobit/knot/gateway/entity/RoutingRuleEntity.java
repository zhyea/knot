package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleEntity {
    private Long id;
    private String ruleCode;
    private String name;
    private String appScenario;
    private String modelTypes;
    private Long appId;
    private String appName;
    private Long userId;
    /** 关联 users，仅查询展示 */
    private String userRealName;
    private String userUsername;
    private Long fallbackRuleId;
    private String status;
}
