package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.constants.enums.QuotaWindowEnum;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.entity.QuotaPolicyEntity;
import org.chobit.knot.gateway.entity.RateLimitPolicyEntity;
import org.chobit.knot.gateway.entity.ResourceTrafficPolicyEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.QuotaPolicyMapper;
import org.chobit.knot.gateway.mapper.RateLimitPolicyMapper;
import org.chobit.knot.gateway.mapper.ResourceTrafficPolicyMapper;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 频控与额度策略支持：独立策略表加 {@code resource_traffic_policies} 资源绑定。
 *
 * <p>绑定列一律存业务码（resource_code / rate_limit_policy_code / quota_policy_code），
 * 资源主键 id 由 mapper 多态 join 派生，对外接口仍按 id 收发，调用方零感知。</p>
 */
@Component
public class ResourceTrafficPolicySupport {

    private final ResourceTrafficPolicyMapper bindingMapper;
    private final RateLimitPolicyMapper rateLimitPolicyMapper;
    private final QuotaPolicyMapper quotaPolicyMapper;

    /**
     * Constructs a new instance.
     */
    public ResourceTrafficPolicySupport(ResourceTrafficPolicyMapper bindingMapper,
                                        RateLimitPolicyMapper rateLimitPolicyMapper,
                                        QuotaPolicyMapper quotaPolicyMapper) {
        this.bindingMapper = bindingMapper;
        this.rateLimitPolicyMapper = rateLimitPolicyMapper;
        this.quotaPolicyMapper = quotaPolicyMapper;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public TrafficPolicies load(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return new TrafficPolicies(null, null);
        }
        Map<Long, TrafficPolicies> batch = loadBatch(resourceType, List.of(resourceId));
        return batch.getOrDefault(resourceId, new TrafficPolicies(null, null));
    }

    /**
     * Executes the public operation. Executes the public operation.
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
        Set<String> quotaCodes = new HashSet<>();
        for (ResourceTrafficPolicyEntity b : bindings) {
            if (b.getRateLimitPolicyCode() != null) {
                rateCodes.add(b.getRateLimitPolicyCode());
            }
            if (b.getQuotaPolicyCode() != null) {
                quotaCodes.add(b.getQuotaPolicyCode());
            }
        }

        Map<String, RateLimitPolicyEntity> rateMap = rateCodes.isEmpty()
                ? Map.of()
                : rateLimitPolicyMapper.listByCodes(List.copyOf(rateCodes)).stream()
                .collect(Collectors.toMap(RateLimitPolicyEntity::getPolicyCode, Function.identity(), (a, b) -> a));

        Map<String, QuotaPolicyEntity> quotaMap = quotaCodes.isEmpty()
                ? Map.of()
                : quotaPolicyMapper.listByCodes(List.copyOf(quotaCodes)).stream()
                .collect(Collectors.toMap(QuotaPolicyEntity::getPolicyCode, Function.identity(), (a, b) -> a));

        Map<Long, TrafficPolicies> result = new HashMap<>();
        for (ResourceTrafficPolicyEntity b : bindings) {
            RateLimitPolicy rate = b.getRateLimitPolicyCode() == null
                    ? null : toRateLimitModel(rateMap.get(b.getRateLimitPolicyCode()));
            QuotaPolicy quota = b.getQuotaPolicyCode() == null
                    ? null : toQuotaModel(quotaMap.get(b.getQuotaPolicyCode()));
            Long resourceId = b.getResourceId();
            if (resourceId != null) {
                result.put(resourceId, new TrafficPolicies(rate, quota));
            }
        }
        return result;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void save(String resourceType, Long resourceId, RateLimitPolicy rateLimit, QuotaPolicy quota) {
        if (resourceId == null) {
            return;
        }
        // 每层只落自己那一类策略：模型 / 路由规则只存限流，应用 / 供应商 / 消费者只存额度，
        // 传错方向的策略在这里被丢掉，避免绑定表里出现永不被判定的脏数据
        RateLimitPolicy effectiveRateLimit = supportsRateLimit(resourceType) ? rateLimit : null;
        QuotaPolicy effectiveQuota = supportsQuota(resourceType) ? quota : null;

        ResourceTrafficPolicyEntity binding = bindingMapper.getByResource(resourceType, resourceId);
        String rateCode = binding != null ? binding.getRateLimitPolicyCode() : null;
        String quotaCode = binding != null ? binding.getQuotaPolicyCode() : null;

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

        if (isEmptyQuota(effectiveQuota)) {
            if (quotaCode != null) {
                deleteQuotaByCode(quotaCode);
                quotaCode = null;
            }
        } else {
            if (quotaCode == null) {
                quotaCode = insertQuota(resourceType, resourceId, effectiveQuota);
            } else {
                updateQuota(quotaCode, resourceType, resourceId, effectiveQuota);
            }
        }

        if (rateCode == null && quotaCode == null) {
            if (binding != null) {
                bindingMapper.deleteById(binding.getId());
            }
            return;
        }

        if (binding == null) {
            ResourceTrafficPolicyEntity created = new ResourceTrafficPolicyEntity();
            created.setResourceType(resourceType);
            created.setResourceCode(requireResourceCode(resourceType, resourceId));
            created.setRateLimitPolicyCode(rateCode);
            created.setQuotaPolicyCode(quotaCode);
            bindingMapper.insert(created);
        } else {
            binding.setRateLimitPolicyCode(rateCode);
            binding.setQuotaPolicyCode(quotaCode);
            bindingMapper.update(binding);
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

    private void deleteQuotaByCode(String policyCode) {
        QuotaPolicyEntity entity = quotaPolicyMapper.getByCode(policyCode);
        if (entity != null) {
            quotaPolicyMapper.deleteById(entity.getId());
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

    private String insertQuota(String resourceType, Long resourceId, QuotaPolicy policy) {
        QuotaPolicyEntity entity = new QuotaPolicyEntity();
        entity.setPolicyCode(policyCode(resourceType, resourceId, "QT"));
        entity.setPolicyName(policyName(resourceType, resourceId, "额度"));
        fillQuota(entity, policy);
        entity.setStatus(EnabledStatusEnum.ENABLED.code());
        quotaPolicyMapper.insert(entity);
        return entity.getPolicyCode();
    }

    private void updateQuota(String policyCode, String resourceType, Long resourceId, QuotaPolicy policy) {
        QuotaPolicyEntity entity = quotaPolicyMapper.getByCode(policyCode);
        if (entity == null) {
            insertQuota(resourceType, resourceId, policy);
            return;
        }
        fillQuota(entity, policy);
        quotaPolicyMapper.update(entity);
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
     * Converts the source value to the target representation. Executes the public operation.
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
     * Converts the source value to the target representation. Executes the public operation.
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
}
