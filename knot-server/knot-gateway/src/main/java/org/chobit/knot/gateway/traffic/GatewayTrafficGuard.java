package org.chobit.knot.gateway.traffic;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关流量守卫：频控 + 额度。
 *
 * <h3>分层</h3>
 * <ul>
 *   <li>本类只负责<b>业务判定</b>：哪些资源要检查、限额是多少、超了算什么原因；</li>
 *   <li>计数一律下沉到 {@link TrafficCounterStore}，单节点 / 多节点只是换实现，判定代码不动。</li>
 * </ul>
 *
 * <h3>口径</h3>
 * <ul>
 *   <li><b>频控</b>：请求进入即计数（{@code +1}，无论上游成败），秒级 / 分钟级固定窗口，
 *       阈值取 {@code per_second} / {@code per_minute}；</li>
 *   <li><b>额度</b>：请求前只判断「已用量是否达到上限」，请求成功后再按真实用量累加
 *       （日 / 月窗口计请求数，累计维度计 token）。上游失败不消耗额度。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class GatewayTrafficGuard {

    private final TrafficPolicySource policySource;
    private final TrafficCounterStore counterStore;
    private final TrafficCounterKeys counterKeys;

    /**
     * 创建一次请求的检查上下文：缓存同一请求内重复解析出来的供应商账户主键。
     */
    public TrafficCheckContext newContext() {
        return new TrafficCheckContext();
    }

    /**
     * 检查一次网关请求中固定不变的资源：应用、路由规则、消费者。
     *
     * @return 放行或首个被拒原因
     */
    public TrafficDecision checkRouting(ResolvedRouting routing) {
        return checkResources(routingResources(routing), Instant.now());
    }

    /**
     * 检查当前路由目标：目标模型 + 其供应商账户。
     *
     * @return 放行或首个被拒原因
     */
    public TrafficDecision checkTarget(RoutingRuleTargetDto target, TrafficCheckContext context) {
        return checkResources(targetResources(target, context), Instant.now());
    }

    /**
     * 上游调用成功后记账：把本次用量累加到本请求命中的全部资源上。
     *
     * @param routing 已解析的路由（应用 / 规则 / 消费者）
     * @param target 实际调用成功的路由目标（模型 / 供应商）
     * @param tokens 本次调用消耗的 token 数，取不到时为 {@code 0}
     * @param context 与 {@link #checkTarget} 共用的请求上下文
     */
    public void record(ResolvedRouting routing,
                       RoutingRuleTargetDto target,
                       long tokens,
                       TrafficCheckContext context) {
        List<ResourceRef> refs = new ArrayList<>(routingResources(routing));
        refs.addAll(targetResources(target, context));
        Instant now = Instant.now();
        for (ResourceRef ref : refs) {
            QuotaPolicy quota = quotaPolicyOf(ref);
            if (quota == null) {
                continue;
            }
            for (QuotaDimension dimension : QuotaDimension.ALL) {
                if (dimension.limitOf(quota) <= 0L) {
                    continue;
                }
                long delta = dimension.requestBased() ? 1L : tokens;
                if (delta <= 0L) {
                    continue;
                }
                TrafficCounterKey key = counterKeys.quota(ref.type(), ref.id(), dimension, now);
                counterStore.addAndGet(key.key(), delta, key.expireAtMillis());
            }
        }
    }

    private TrafficDecision checkResources(List<ResourceRef> refs, Instant now) {
        long nowMillis = now.toEpochMilli();
        for (ResourceRef ref : refs) {
            TrafficPolicies policies = policySource.policiesOf(ref.type(), ref.id());
            TrafficDecision rateLimit = checkRateLimit(ref, policies == null ? null : policies.rateLimitPolicy(), nowMillis);
            if (!rateLimit.allowed()) {
                return rateLimit;
            }
            TrafficDecision quota = checkQuota(ref, policies == null ? null : policies.quotaPolicy(), now);
            if (!quota.allowed()) {
                return quota;
            }
        }
        return TrafficDecision.allow();
    }

    /**
     * 频控：请求进入即计数，超出窗口阈值即拒绝。
     */
    private TrafficDecision checkRateLimit(ResourceRef ref, RateLimitPolicy policy, long nowMillis) {
        if (policy == null) {
            return TrafficDecision.allow();
        }
        for (RateLimitWindow window : RateLimitWindow.ALL) {
            int limit = window.limitOf(policy);
            if (limit <= 0) {
                continue;
            }
            TrafficCounterKey key = counterKeys.rateLimit(ref.type(), ref.id(), window, nowMillis);
            long used = counterStore.addAndGet(key.key(), 1L, key.expireAtMillis());
            if (used > limit) {
                return TrafficDecision.reject(TrafficRejectReason.RATE_LIMIT,
                        ref.type(), ref.id(), limit, used, key.expireAtMillis());
            }
        }
        return TrafficDecision.allow();
    }

    /**
     * 额度：事后累计，所以这里只读不写——上游失败的请求不消耗额度。
     */
    private TrafficDecision checkQuota(ResourceRef ref, QuotaPolicy policy, Instant now) {
        if (policy == null) {
            return TrafficDecision.allow();
        }
        for (QuotaDimension dimension : QuotaDimension.ALL) {
            long limit = dimension.limitOf(policy);
            if (limit <= 0L) {
                continue;
            }
            TrafficCounterKey key = counterKeys.quota(ref.type(), ref.id(), dimension, now);
            long used = counterStore.get(key.key());
            if (used >= limit) {
                return TrafficDecision.reject(dimension.rejectReason(),
                        ref.type(), ref.id(), limit, used, key.expireAtMillis());
            }
        }
        return TrafficDecision.allow();
    }

    private List<ResourceRef> routingResources(ResolvedRouting routing) {
        List<ResourceRef> refs = new ArrayList<>(3);
        if (routing == null) {
            return refs;
        }
        if (routing.routingInfo() != null
                && routing.routingInfo().app() != null
                && routing.routingInfo().app().id() != null) {
            refs.add(new ResourceRef(TrafficResourceTypeEnum.APP.code(), routing.routingInfo().app().id()));
        }
        if (routing.ruleId() != null) {
            refs.add(new ResourceRef(TrafficResourceTypeEnum.ROUTING_RULE.code(), routing.ruleId()));
        }
        if (routing.consumerId() != null) {
            refs.add(new ResourceRef(TrafficResourceTypeEnum.ROUTING_CONSUMER.code(), routing.consumerId()));
        }
        return refs;
    }

    private List<ResourceRef> targetResources(RoutingRuleTargetDto target, TrafficCheckContext context) {
        List<ResourceRef> refs = new ArrayList<>(2);
        if (target == null) {
            return refs;
        }
        if (target.targetId() != null) {
            refs.add(new ResourceRef(TrafficResourceTypeEnum.MODEL.code(), target.targetId()));
        }
        Long providerId = resolveProviderId(target.providerAccountCode(), context);
        if (providerId != null) {
            refs.add(new ResourceRef(TrafficResourceTypeEnum.PROVIDER.code(), providerId));
        }
        return refs;
    }

    /**
     * 供应商账户按 code 绑定策略，但频控资源列仍是账户主键 id，故这里解析成 id。
     */
    private Long resolveProviderId(String providerAccountCode, TrafficCheckContext context) {
        if (providerAccountCode == null) {
            return null;
        }
        return context.providerIds.computeIfAbsent(providerAccountCode, policySource::providerAccountIdOf);
    }

    private QuotaPolicy quotaPolicyOf(ResourceRef ref) {
        TrafficPolicies policies = policySource.policiesOf(ref.type(), ref.id());
        return policies == null ? null : policies.quotaPolicy();
    }

    /**
     * 受控资源引用。
     */
    private record ResourceRef(String type, Long id) {
    }

    /**
     * 单次请求的检查上下文：缓存供应商 code → id 的解析结果。
     */
    public static final class TrafficCheckContext {

        private final Map<String, Long> providerIds = new HashMap<>();
    }
}
