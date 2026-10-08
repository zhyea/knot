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
    /** 规则级失败重试策略（JSON，RetryPolicy 序列化结果）；空表示走内置默认策略 */
    private String retryPolicy;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
