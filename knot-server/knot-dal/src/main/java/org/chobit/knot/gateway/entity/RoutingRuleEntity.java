package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleEntity {
    private Long id;
    private String ruleCode;
    private String name;
    private String appScenario;
    private Long appId;
    private String appName;
    private Long userId;
    /** 关联 ks_users，仅查询展示 */
    private String userRealName;
    private String userUsername;
    /** 规则级失败重试策略（JSON，RetryPolicy 序列化结果）；空表示走内置默认策略 */
    private String retryPolicy;
    private String status;
}
