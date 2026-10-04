package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.model.TrafficPolicies;

/**
 * 流量策略数据源：把「策略从哪来」与「计数存哪去」并列成两个可替换依赖。
 *
 * <p>网关运行时由 {@code GatewayDataService} 适配（走库 + 10 分钟缓存），
 * 单测里可以直接给一份固定策略，不需要拉起数据源。</p>
 */
public interface TrafficPolicySource {

    /**
     * 读取资源的频控与额度策略；未配置时返回两个分量都为 {@code null} 的策略对象。
     */
    TrafficPolicies policiesOf(String resourceType, Long resourceId);

    /**
     * 供应商账户 code → 主键 id；账户不存在时返回 {@code null}。
     */
    Long providerAccountIdOf(String providerAccountCode);
}
