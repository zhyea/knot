package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleEntity {
    private Long id;
    private String ruleCode;
    private String name;
    private String appScenario;
    /** 绑定应用业务码（kb_apps.app_code），非主键 id；落库列 */
    private String appCode;
    /** 应用主键 id（由 join kb_apps 派生，不落库）；VO 仍以 id 为值，admin 内部解析 code↔PK */
    private Long appId;
    /** 应用名称（由 join kb_apps 派生，不落库） */
    private String appName;
    /** 归属用户登录名（业务码，非主键 id）；路由规则按 username 绑定用户，便于跨库稳定归因 */
    private String username;
    /** 规则级失败重试策略（JSON，RetryPolicy 序列化结果）；空表示走内置默认策略 */
    private String retryPolicy;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
