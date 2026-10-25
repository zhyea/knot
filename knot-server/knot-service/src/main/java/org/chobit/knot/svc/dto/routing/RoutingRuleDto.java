package org.chobit.knot.svc.dto.routing;

import org.chobit.knot.common.model.EjectionPolicy;
import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.common.model.RetryPolicy;

import java.time.LocalDateTime;
import java.util.List;

public record RoutingRuleDto(
        Long id,
        String ruleCode,
        String name,
        String appScenario,
        /** 绑定消费者业务码（kb_routing_consumers.consumer_code），一对一，非主键 id；尽量不通过 ID 建立关系 */
        String consumerCode,
        /** 消费者名称（由后端 join kb_routing_consumers 派生，仅展示用） */
        String consumerName,
        Long appId,
        String appName,
        /** 归属用户登录名（业务码，非主键 id）；路由规则按 username 绑定用户 */
        String username,
        /** 归属用户真实姓名（由后端 join ks_users 取真实名，列表展示「姓名(用户名)」用） */
        String userRealName,
        /** 统一模型 code（kb_logical_models.model_code），非必填；选中后目标选择器优先置前对应项（仅排序，不过滤） */
        String logicalModelCode,
        /** 统一模型展示名（由 join kb_logical_models 派生，等于 model_name，不落库） */
        String logicalModelName,
        boolean enabled,
        List<RoutingRuleTargetDto> targets,
        RateLimitPolicy rateLimitPolicy,
        List<QuotaPolicy> quotaPolicies,
        /** 规则级失败重试策略；null 表示未配置，运行时按内置默认（开启）处理 */
        RetryPolicy retryPolicy,
        /** 规则级异常摘除策略；null 表示未配置，运行时按内置默认（关闭）处理 */
        EjectionPolicy ejectionPolicy,
        /** 路由目标选择策略（ModelPoolSelectionStrategyEnum）：PRIORITY 固定序；LEAST_LOAD 按在途并发动态重排 */
        String selectionStrategy,
        /** 创建时间 */
        LocalDateTime createdAt,
        /** 更新时间 */
        LocalDateTime updatedAt,
        /**
         * 规则编码的绑定处数（路由目标行 + 限流绑定行）：&gt;0 时前端锁定「规则编码」输入框，
         * 后端 {@code update} 亦按同一口径拒绝改码。派生字段，不落库。
         */
        Long boundCount
) {
}
