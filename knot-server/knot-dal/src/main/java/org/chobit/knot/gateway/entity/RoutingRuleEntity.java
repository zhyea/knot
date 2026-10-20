package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

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
    /** 归属用户真实姓名（由 join ks_users 派生，不落库）；列表展示用「姓名(用户名)」 */
    private String userRealName;
    /** 绑定消费者业务码（kb_routing_consumers.consumer_code），一对一，非主键 id；落库列 */
    private String consumerCode;
    /** 消费者名称（由 join kb_routing_consumers 派生，不落库） */
    private String consumerName;
    /** 统一模型 code（kb_logical_models.model_code），非必填；选中后目标选择器优先置前对应项（仅排序，不过滤） */
    private String logicalModelCode;
    /** 统一模型展示名（由 join kb_logical_models 派生，等于 model_name，不落库） */
    private String logicalModelName;
    /** 规则级失败重试策略（JSON，RetryPolicy 序列化结果）；空表示走内置默认策略 */
    private String retryPolicy;
    /** 规则级异常摘除策略（JSON，EjectionPolicy 序列化结果）；空表示走内置默认策略（默认关闭） */
    private String ejectionPolicy;
    /** 路由目标选择策略（ModelPoolSelectionStrategyEnum）：PRIORITY 固定序；LEAST_LOAD 按在途并发动态重排 */
    private String selectionStrategy;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    /** 创建时间（由 DB created_at 派生，不落库写路径） */
    private LocalDateTime createdAt;
    /** 更新时间（由 DB updated_at 派生，不落库写路径） */
    private LocalDateTime updatedAt;
}
