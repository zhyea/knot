package org.chobit.knot.gateway.traffic;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.enums.QuotaWindowEnum;
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
 * 网关流量守卫：限流 + 限额。
 *
 * <h3>分层</h3>
 * <ul>
 *   <li>本类只负责<b>业务判定</b>：哪些资源要检查、限额是多少、超了算什么原因；</li>
 *   <li>策略来源 {@link TrafficPolicySource} 与计数存储 {@link TrafficCounterStore}
 *       都是可替换依赖，单节点 / 多节点只是换实现，判定代码不动。</li>
 * </ul>
 *
 * <h3>口径</h3>
 * <ul>
 *   <li><b>限流</b>（模型 / 路由规则）：RPM 请求进入即 +1，超出即拒；
 *       TPM 只能事后累加——请求前拿不到 token 数，超限在<b>后续请求</b>上拦住。</li>
 *   <li><b>限额</b>（应用 / 供应商账户 / 消费者）：窗口内 token 与成本上限，
 *       窗口由策略指定（分钟 / 小时 / 天 / 周 / 月），请求成功后才记账，
 *       上游失败与 failover 重试不消耗额度。</li>
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
     * @param usage  本次用量（token 与成本）
     * @param context 与 {@link #checkTarget} 共用的请求上下文
     */
    public void record(ResolvedRouting routing,
                       RoutingRuleTargetDto target,
                       TrafficUsage usage,
                       TrafficCheckContext context) {
        List<ResourceRef> refs = new ArrayList<>(routingResources(routing));
        refs.addAll(targetResources(target, context));
        Instant now = Instant.now();
        for (ResourceRef ref : refs) {
            TrafficPolicies policies = policySource.policiesOf(ref.type(), ref.id());
            if (policies != null) {
                accumulate(ref, policies, usage, now);
            }
        }
    }

    private TrafficDecision checkResources(List<ResourceRef> refs, Instant now) {
        long nowMillis = now.toEpochMilli();
        for (ResourceRef ref : refs) {
            TrafficResourceTypeEnum type = TrafficResourceTypeEnum.ofCode(ref.type());
            if (type == null) {
                continue;
            }
            TrafficPolicies policies = policySource.policiesOf(ref.type(), ref.id());
            RateLimitPolicy rateLimit = policies == null ? null : policies.rateLimitPolicy();
            QuotaPolicy quota = policies == null ? null : policies.quotaPolicy();
            if (type.supportsRateLimit()) {
                TrafficDecision decision = checkRateLimit(ref, rateLimit, nowMillis);
                if (!decision.allowed()) {
                    return decision;
                }
            }
            if (type.supportsQuota()) {
                TrafficDecision decision = checkQuota(ref, quota, now);
                if (!decision.allowed()) {
                    return decision;
                }
            }
        }
        return TrafficDecision.allow();
    }

    /**
     * 限流：RPM 事前计数、TPM 只读判断（TPM 的用量在 {@link #record} 里累加）。
     */
    private TrafficDecision checkRateLimit(ResourceRef ref, RateLimitPolicy policy, long nowMillis) {
        if (policy == null) {
            return TrafficDecision.allow();
        }
        for (RateLimitDimension dimension : RateLimitDimension.ALL) {
            int limit = dimension.limitOf(policy);
            if (limit <= 0) {
                continue;
            }
            TrafficCounterKey key = counterKeys.rateLimit(ref.type(), ref.id(), dimension, nowMillis);
            long used = dimension.requestBased()
                    ? counterStore.addAndGet(key.key(), 1L, key.expireAtMillis())
                    : counterStore.get(key.key());
            boolean exceeded = dimension.requestBased() ? used > limit : used >= limit;
            if (exceeded) {
                return reject(dimension.rejectReason(), ref, limit, used, key.expireAtMillis());
            }
        }
        return TrafficDecision.allow();
    }

    /**
     * 限额：窗口内累计量，只读判断——上游失败的请求不消耗额度。
     */
    private TrafficDecision checkQuota(ResourceRef ref, QuotaPolicy policy, Instant now) {
        if (policy == null) {
            return TrafficDecision.allow();
        }
        QuotaWindowEnum window = QuotaWindowEnum.fromCode(policy.window());
        for (QuotaDimension dimension : QuotaDimension.ALL) {
            long limit = dimension.limitOf(policy);
            if (limit <= 0L) {
                continue;
            }
            TrafficCounterKey key = counterKeys.quota(ref.type(), ref.id(), dimension, window, policy.currency(), now);
            long used = counterStore.get(key.key());
            if (used >= limit) {
                return reject(dimension.rejectReason(), ref, limit, used, key.expireAtMillis());
            }
        }
        return TrafficDecision.allow();
    }

    /**
     * 按资源所属层累加：限流层只累加 TPM，限额层累加 token 与成本。
     */
    private void accumulate(ResourceRef ref, TrafficPolicies policies, TrafficUsage usage, Instant now) {
        TrafficResourceTypeEnum type = TrafficResourceTypeEnum.ofCode(ref.type());
        if (type == null) {
            return;
        }
        if (type.supportsRateLimit()) {
            RateLimitPolicy rateLimit = policies.rateLimitPolicy();
            if (rateLimit != null && rateLimit.tpm() > 0 && usage.tokens() > 0L) {
                TrafficCounterKey key = counterKeys.rateLimit(ref.type(), ref.id(),
                        RateLimitDimension.TPM, now.toEpochMilli());
                counterStore.addAndGet(key.key(), usage.tokens(), key.expireAtMillis());
            }
        }
        if (!type.supportsQuota()) {
            return;
        }
        QuotaPolicy quota = policies.quotaPolicy();
        if (quota == null) {
            return;
        }
        QuotaWindowEnum window = QuotaWindowEnum.fromCode(quota.window());
        if (quota.maxTokens() > 0L && usage.tokens() > 0L) {
            TrafficCounterKey key = counterKeys.quota(ref.type(), ref.id(),
                    QuotaDimension.TOKENS, window, null, now);
            counterStore.addAndGet(key.key(), usage.tokens(), key.expireAtMillis());
        }
        if (QuotaDimension.COST.limitOf(quota) > 0L
                && usage.hasCost()
                && usage.currency().equalsIgnoreCase(quota.currency())) {
            TrafficCounterKey key = counterKeys.quota(ref.type(), ref.id(),
                    QuotaDimension.COST, window, quota.currency(), now);
            counterStore.addAndGet(key.key(), QuotaDimension.toScaledAmount(usage.cost()), key.expireAtMillis());
        }
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
     * 供应商账户按 code 绑定策略，但受控资源列仍是账户主键 id，故这里解析成 id。
     */
    private Long resolveProviderId(String providerAccountCode, TrafficCheckContext context) {
        if (providerAccountCode == null) {
            return null;
        }
        return context.providerIds.computeIfAbsent(providerAccountCode, policySource::providerAccountIdOf);
    }

    private static TrafficDecision reject(TrafficRejectReason reason,
                                          ResourceRef ref,
                                          long limit,
                                          long used,
                                          long resetAtMillis) {
        return TrafficDecision.reject(reason, ref.type(), ref.id(), limit, used, resetAtMillis);
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
