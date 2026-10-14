package org.chobit.knot.gateway.vo.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.chobit.knot.gateway.model.EjectionPolicy;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.RetryPolicy;

import java.time.LocalDateTime;
import java.util.List;

public record RoutingRule(
        Long id,
        @Size(max = 32) String ruleCode,
        @NotBlank String name,
        @Size(max = 128) String appScenario,
        List<Long> consumerIds,
        List<String> consumerNames,
        Long appId,
        String appName,
        /** 归属用户登录名（业务码，非主键 id）；路由规则按 username 绑定用户 */
        String username,
        /** 归属用户真实姓名（由后端 join ks_users 取真实名，列表展示「姓名(用户名)」用） */
        String userRealName,
        /** 统一模型 code（kb_logical_models.model_code），非必填；选中后目标选择器优先置前对应项（仅排序，不过滤） */
        @Size(max = 128) String logicalModelCode,
        /** 统一模型展示名（由 join kb_logical_models 派生，不落库） */
        @Size(max = 128) String logicalModelName,
        boolean enabled,
        @Valid List<RoutingRuleTargetItem> targets,
        RateLimitPolicy rateLimitPolicy,
        List<QuotaPolicy> quotaPolicies,
        /** 规则级失败重试策略；null 表示未配置，运行时按内置默认（开启）处理 */
        RetryPolicy retryPolicy,
        /** 规则级异常摘除策略；null 表示未配置，运行时按内置默认（关闭）处理 */
        EjectionPolicy ejectionPolicy,
        /** 路由目标选择策略（ModelPoolSelectionStrategyEnum）：PRIORITY 固定序；LEAST_LOAD 按在途并发动态重排 */
        @Size(max = 32) String selectionStrategy,
        /** 创建时间 */
        LocalDateTime createdAt,
        /** 更新时间 */
        LocalDateTime updatedAt
) {
}
