package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.QuotaWindowEnum;
import org.chobit.knot.common.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.dal.entity.QuotaPolicyEntity;
import org.chobit.knot.dal.entity.RateLimitPolicyEntity;
import org.chobit.knot.dal.entity.ResourceQuotaBindingEntity;
import org.chobit.knot.dal.entity.ResourceTrafficPolicyEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.QuotaPolicyMapper;
import org.chobit.knot.dal.mapper.RateLimitPolicyMapper;
import org.chobit.knot.dal.mapper.ResourceQuotaBindingMapper;
import org.chobit.knot.dal.mapper.ResourceTrafficPolicyMapper;
import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 频控与额度策略支持：独立策略表加 {@code resource_traffic_policies} 资源绑定（限流），
 * 额度走 {@code resource_quota_bindings} 子表，一个资源可挂多套不同统计窗口的额度策略。
 *
 * <p>绑定列一律存业务码（resource_code / rate_limit_policy_code / quota_policy_code），
 * 资源主键 id 由 mapper 多态 join 派生，对外接口仍按 id 收发，调用方零感知。</p>
 */
@Component
public class ResourceTrafficPolicySupport {

    private final ResourceTrafficPolicyMapper bindingMapper;
    private final RateLimitPolicyMapper rateLimitPolicyMapper;
    private final QuotaPolicyMapper quotaPolicyMapper;
    private final ResourceQuotaBindingMapper quotaBindingMapper;

    /**
     * Constructs a new instance.
     */
    public ResourceTrafficPolicySupport(ResourceTrafficPolicyMapper bindingMapper,
                                        RateLimitPolicyMapper rateLimitPolicyMapper,
                                        QuotaPolicyMapper quotaPolicyMapper,
                                        ResourceQuotaBindingMapper quotaBindingMapper) {
        this.bindingMapper = bindingMapper;
        this.rateLimitPolicyMapper = rateLimitPolicyMapper;
        this.quotaPolicyMapper = quotaPolicyMapper;
        this.quotaBindingMapper = quotaBindingMapper;
    }

    /**
     * 读取单资源的频控与额度策略。
     */
    public TrafficPolicies load(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return new TrafficPolicies(null, List.of());
        }
        Map<Long, TrafficPolicies> batch = loadBatch(resourceType, List.of(resourceId));
        return batch.getOrDefault(resourceId, new TrafficPolicies(null, List.of()));
    }

    /**
     * 批量读取：限流走 resource_traffic_policies，额度走 resource_quota_bindings 子表。
     */
    public Map<Long, TrafficPolicies> loadBatch(String resourceType, List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return Map.of();
        }
        List<ResourceTrafficPolicyEntity> bindings =
                bindingMapper.listByResources(resourceType, resourceIds);
        if (bindings.isEmpty()) {
            return Map.of();
        }

        Set<String> rateCodes = new HashSet<>();
        Set<String> resourceCodes = new HashSet<>();
        for (ResourceTrafficPolicyEntity b : bindings) {
            if (b.getRateLimitPolicyCode() != null) {
                rateCodes.add(b.getRateLimitPolicyCode());
            }
            if (b.getResourceCode() != null) {
                resourceCodes.add(b.getResourceCode());
            }
        }

        Map<String, RateLimitPolicyEntity> rateMap = rateCodes.isEmpty()
                ? Map.of()
                : rateLimitPolicyMapper.listByCodes(List.copyOf(rateCodes)).stream()
                .collect(Collectors.toMap(RateLimitPolicyEntity::getPolicyCode, Function.identity(), (a, b) -> a));

        // 额度经子表：先取绑定，再批量取 policy，最后按资源业务码分组
        List<ResourceQuotaBindingEntity> quotaBindings = resourceCodes.isEmpty()
                ? List.of()
                : quotaBindingMapper.listByResources(resourceType, List.copyOf(resourceCodes));

        Set<String> quotaCodes = new HashSet<>();
        for (ResourceQuotaBindingEntity qb : quotaBindings) {
            if (qb.getQuotaPolicyCode() != null) {
                quotaCodes.add(qb.getQuotaPolicyCode());
            }
        }
        Map<String, QuotaPolicyEntity> quotaMap = quotaCodes.isEmpty()
                ? Map.of()
                : quotaPolicyMapper.listByCodes(List.copyOf(quotaCodes)).stream()
                .collect(Collectors.toMap(QuotaPolicyEntity::getPolicyCode, Function.identity(), (a, b) -> a));

        Map<String, List<QuotaPolicy>> quotaByResource = new HashMap<>();
        for (ResourceQuotaBindingEntity qb : quotaBindings) {
            QuotaPolicy q = toQuotaModel(quotaMap.get(qb.getQuotaPolicyCode()));
            if (q == null) {
                continue;
            }
            quotaByResource.computeIfAbsent(qb.getResourceCode(), k -> new ArrayList<>()).add(q);
        }

        Map<Long, TrafficPolicies> result = new HashMap<>();
        for (ResourceTrafficPolicyEntity b : bindings) {
            RateLimitPolicy rate = b.getRateLimitPolicyCode() == null
                    ? null : toRateLimitModel(rateMap.get(b.getRateLimitPolicyCode()));
            List<QuotaPolicy> quotas = b.getResourceCode() == null
                    ? List.of() : quotaByResource.getOrDefault(b.getResourceCode(), List.of());
            Long resourceId = b.getResourceId();
            if (resourceId != null) {
                result.put(resourceId, new TrafficPolicies(rate, quotas));
            }
        }
        return result;
    }

    /**
     * 统计某资源业务码上的频控 + 额度绑定行数。
     *
     * <p>改码守卫用：两张绑定表的 resource_code 存业务码，资源改码后行还在、join 落空，
     * 限流与额度会静默失效（不报错、不生效），因此「有绑定就不许改码」。
     * 注意 PROVIDER 层指向的是 {@code kb_provider_accounts.code}（供应商账户），不是供应商主表。</p>
     */
    public long countBindings(String resourceType, String resourceCode) {
        if (resourceType == null || resourceCode == null || resourceCode.isBlank()) {
            return 0L;
        }
        long traffic = nz(bindingMapper.countByResourceCode(resourceType, resourceCode));
        long quota = nz(quotaBindingMapper.countByResourceCode(resourceType, resourceCode));
        return traffic + quota;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 写入：限流与额度按资源分层收口。额度支持一个资源挂多套（按窗口）的额度策略。
     */
    public void save(String resourceType, Long resourceId, RateLimitPolicy rateLimit, List<QuotaPolicy> quotas) {
        if (resourceId == null) {
            return;
        }
        // 每层只落自己那一类策略：模型 / 路由规则只存限流，应用 / 供应商 / 消费者只存额度，
        // 传错方向的策略在这里被丢掉，避免绑定表里出现永不被判定的脏数据
        RateLimitPolicy effectiveRateLimit = supportsRateLimit(resourceType) ? rateLimit : null;
        // 与限流对称：整张空卡片（maxTokens<=0 且无成本上限）不落库，避免子表堆积"什么都不限"的脏策略
        List<QuotaPolicy> effectiveQuotas = supportsQuota(resourceType) && quotas != null
                ? quotas.stream().filter(q -> !isEmptyQuota(q)).toList()
                : List.of();

        ResourceTrafficPolicyEntity binding = bindingMapper.getByResource(resourceType, resourceId);
        String rateCode = binding != null ? binding.getRateLimitPolicyCode() : null;
        String resourceCode = binding != null ? binding.getResourceCode() : null;
        if (resourceCode == null) {
            resourceCode = requireResourceCode(resourceType, resourceId);
        }

        // 限流（模型 / 路由规则）：原样 upsert 单策略
        if (isEmptyRateLimit(effectiveRateLimit)) {
            if (rateCode != null) {
                deleteRateLimitByCode(rateCode);
                rateCode = null;
            }
        } else {
            if (rateCode == null) {
                rateCode = insertRateLimit(resourceType, resourceId, effectiveRateLimit);
            } else {
                updateRateLimit(rateCode, resourceType, resourceId, effectiveRateLimit);
            }
        }

        // 额度（应用 / 供应商 / 消费者）：子表整组替换 + 删孤儿
        saveQuotas(resourceType, resourceId, effectiveQuotas, resourceCode);

        // 聚合行：只要有限流或额度就保留。额度落在子表，但聚合行必须存在一行使 loadBatch 能命中该资源
        boolean hasQuota = !effectiveQuotas.isEmpty();
        if (rateCode == null && !hasQuota) {
            if (binding != null) {
                bindingMapper.deleteById(binding.getId());
            }
            return;
        }
        if (binding == null) {
            ResourceTrafficPolicyEntity created = new ResourceTrafficPolicyEntity();
            created.setResourceType(resourceType);
            created.setResourceCode(resourceCode);
            created.setRateLimitPolicyCode(rateCode);
            bindingMapper.insert(created);
        } else {
            binding.setRateLimitPolicyCode(rateCode);
            bindingMapper.update(binding);
        }
    }

    /**
     * 写入某资源的额度策略组：按窗口 upsert policy 行 + 整组替换子表绑定 + 删孤儿 policy。
     */
    private void saveQuotas(String resourceType, Long resourceId, List<QuotaPolicy> quotas, String resourceCode) {
        Set<String> newCodes = new HashSet<>();
        for (QuotaPolicy q : quotas) {
            String code = quotaPolicyCode(resourceType, resourceId, q.window());
            newCodes.add(code);
            upsertQuota(code, resourceType, resourceId, q);
        }
        quotaBindingMapper.deleteByResource(resourceType, resourceCode);
        if (!newCodes.isEmpty()) {
            List<ResourceQuotaBindingEntity> rows = new ArrayList<>(newCodes.size());
            for (String code : newCodes) {
                ResourceQuotaBindingEntity b = new ResourceQuotaBindingEntity();
                b.setResourceType(resourceType);
                b.setResourceCode(resourceCode);
                b.setQuotaPolicyCode(code);
                rows.add(b);
            }
            quotaBindingMapper.insertBatch(rows);
        }
        deleteOrphanQuotas(resourceType, resourceId, newCodes);
    }

    private void upsertQuota(String policyCode, String resourceType, Long resourceId, QuotaPolicy policy) {
        QuotaPolicyEntity entity = quotaPolicyMapper.getByCode(policyCode);
        if (entity == null) {
            entity = new QuotaPolicyEntity();
            entity.setPolicyCode(policyCode);
            entity.setPolicyName(policyName(resourceType, resourceId, "额度") + " " + policy.window());
            entity.setStatus(EnabledStatusEnum.ENABLED.code());
        }
        fillQuota(entity, policy);
        if (entity.getId() == null) {
            quotaPolicyMapper.insert(entity);
        } else {
            quotaPolicyMapper.update(entity);
        }
    }

    /**
     * 删孤儿额度策略：业务码前缀匹配本资源、却不在本次保留集合内的 policy 行一并清理。
     */
    private void deleteOrphanQuotas(String resourceType, Long resourceId, Set<String> keepCodes) {
        String prefix = quotaPolicyCodePrefix(resourceType, resourceId);
        List<QuotaPolicyEntity> existing = quotaPolicyMapper.listByCodePrefix(prefix);
        for (QuotaPolicyEntity e : existing) {
            if (!keepCodes.contains(e.getPolicyCode())) {
                quotaPolicyMapper.deleteById(e.getId());
            }
        }
    }

    /**
     * 写路径：资源主键解析为业务码（绑定列存 code 不存 id）。
     */
    private String requireResourceCode(String resourceType, Long resourceId) {
        String code = bindingMapper.resolveResourceCode(resourceType, resourceId);
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在，无法绑定频控/额度策略");
        }
        return code;
    }

    private void deleteRateLimitByCode(String policyCode) {
        RateLimitPolicyEntity entity = rateLimitPolicyMapper.getByCode(policyCode);
        if (entity != null) {
            rateLimitPolicyMapper.deleteById(entity.getId());
        }
    }

    private String insertRateLimit(String resourceType, Long resourceId, RateLimitPolicy policy) {
        RateLimitPolicyEntity entity = new RateLimitPolicyEntity();
        entity.setPolicyCode(policyCode(resourceType, resourceId, "RL"));
        entity.setPolicyName(policyName(resourceType, resourceId, "频控"));
        fillRateLimit(entity, policy);
        entity.setStatus(EnabledStatusEnum.ENABLED.code());
        rateLimitPolicyMapper.insert(entity);
        return entity.getPolicyCode();
    }

    private void updateRateLimit(String policyCode, String resourceType, Long resourceId, RateLimitPolicy policy) {
        RateLimitPolicyEntity entity = rateLimitPolicyMapper.getByCode(policyCode);
        if (entity == null) {
            insertRateLimit(resourceType, resourceId, policy);
            return;
        }
        fillRateLimit(entity, policy);
        rateLimitPolicyMapper.update(entity);
    }

    private static void fillRateLimit(RateLimitPolicyEntity entity, RateLimitPolicy policy) {
        entity.setRpm(policy.rpm());
        entity.setTpm(policy.tpm());
    }

    private static void fillQuota(QuotaPolicyEntity entity, QuotaPolicy policy) {
        entity.setMaxTokens(policy.maxTokens());
        entity.setCostLimit(policy.costLimit());
        entity.setCurrency(policy.currency());
        entity.setQuotaWindow(QuotaWindowEnum.fromCode(policy.window()).code());
    }

    /**
     * 实体转模型。停用的额度策略视为无效，返回 null。
     */
    public static RateLimitPolicy toRateLimitModel(RateLimitPolicyEntity entity) {
        if (entity == null || !EnabledStatusEnum.isEnabled(entity.getStatus())) {
            return null;
        }
        return new RateLimitPolicy(
                entity.getRpm() != null ? entity.getRpm() : 0,
                entity.getTpm() != null ? entity.getTpm() : 0
        );
    }

    /**
     * 实体转模型。停用的额度策略视为无效，返回 null。
     */
    public static QuotaPolicy toQuotaModel(QuotaPolicyEntity entity) {
        if (entity == null || !EnabledStatusEnum.isEnabled(entity.getStatus())) {
            return null;
        }
        return new QuotaPolicy(
                entity.getMaxTokens() != null ? entity.getMaxTokens() : 0L,
                entity.getCostLimit(),
                entity.getCurrency(),
                QuotaWindowEnum.fromCode(entity.getQuotaWindow()).code()
        );
    }

    private static boolean isEmptyRateLimit(RateLimitPolicy policy) {
        return policy == null || (policy.rpm() <= 0 && policy.tpm() <= 0);
    }

    private static boolean isEmptyQuota(QuotaPolicy policy) {
        return policy == null
                || (policy.maxTokens() <= 0 && (policy.costLimit() == null || policy.costLimit().signum() <= 0));
    }

    /**
     * 模型与路由规则只配限流（RPM / TPM）。
     */
    private static boolean supportsRateLimit(String resourceType) {
        return TrafficResourceTypeEnum.MODEL.code().equals(resourceType)
                || TrafficResourceTypeEnum.ROUTING_RULE.code().equals(resourceType);
    }

    /**
     * 应用、供应商账户与消费者只配额度（最大 token / 成本上限）。
     */
    private static boolean supportsQuota(String resourceType) {
        return TrafficResourceTypeEnum.APP.code().equals(resourceType)
                || TrafficResourceTypeEnum.PROVIDER.code().equals(resourceType)
                || TrafficResourceTypeEnum.ROUTING_CONSUMER.code().equals(resourceType);
    }

    private static String policyCode(String resourceType, Long resourceId, String suffix) {
        return resourceType + "-" + resourceId + "-" + suffix;
    }

    private static String policyName(String resourceType, Long resourceId, String suffix) {
        return resourceType + " #" + resourceId + " " + suffix;
    }

    private static String quotaPolicyCode(String resourceType, Long resourceId, String window) {
        return resourceType + "-" + resourceId + "-QT-" + window;
    }

    private static String quotaPolicyCodePrefix(String resourceType, Long resourceId) {
        return resourceType + "-" + resourceId + "-QT-";
    }
}
