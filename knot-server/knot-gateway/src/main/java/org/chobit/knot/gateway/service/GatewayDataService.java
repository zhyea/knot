package org.chobit.knot.gateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.chobit.knot.gateway.entity.*;
import org.chobit.knot.gateway.mapper.AppCredentialMapper;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.ModelApiBindingMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.mapper.ModelPoolMapper;
import org.chobit.knot.gateway.mapper.ProviderCredentialMapper;
import org.chobit.knot.gateway.mapper.ProviderAccountMapper;
import org.chobit.knot.gateway.mapper.QuotaPolicyMapper;
import org.chobit.knot.gateway.mapper.RateLimitPolicyMapper;
import org.chobit.knot.gateway.mapper.ResourceTrafficPolicyMapper;
import org.chobit.knot.gateway.mapper.RoutingConsumerMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleTargetMapper;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
public class GatewayDataService {

    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMinutes(10);
    private static final Duration REFRESH_AFTER_WRITE = Duration.ofMinutes(3);

    /**
     * 命中计费规则的缓存时长：与其余缓存一致。
     */
    private static final Duration BILLING_RULE_HIT_TTL = Duration.ofMinutes(10);

    /**
     * 未命中计费规则的缓存时长：显著短于命中时长。
     *
     * <p>管理员刚给模型绑定规则后，若把「查不到」也缓存 10 分钟，计费会整整 10 分钟不出结果
     * （实测复现：先打一次未绑定的请求，再绑定规则，billing 仍是缺失的）。空结果只做防穿透。</p>
     */
    private static final Duration BILLING_RULE_MISS_TTL = Duration.ofSeconds(30);

    private final LoadingCache<String, Optional<AppCredentialEntity>> appCredentialByKeyCache;
    private final LoadingCache<Long, Optional<AppEntity>> appByIdCache;
    private final LoadingCache<String, Optional<RoutingConsumerEntity>> consumerBySecretKeyCache;
    private final LoadingCache<Long, List<RoutingRuleEntity>> enabledRulesByConsumerIdCache;
    private final LoadingCache<ConsumerRuleKey, Optional<RoutingRuleEntity>> enabledRuleByConsumerAndCodeCache;
    private final LoadingCache<Long, List<RoutingRuleTargetEntity>> targetsByRuleIdCache;
    private final LoadingCache<Long, Optional<ModelEntity>> modelByIdCache;
    private final LoadingCache<String, Optional<ModelEntity>> modelByCodeCache;
    private final LoadingCache<Long, Optional<ModelPoolEntity>> modelPoolByIdCache;
    private final LoadingCache<String, List<ModelPoolItemEntity>> modelPoolItemsByPoolCodeCache;
    private final LoadingCache<Long, Optional<ProviderAccountEntity>> providerByIdCache;
    private final LoadingCache<String, Optional<ProviderAccountEntity>> providerByCodeCache;
    private final LoadingCache<Long, Optional<ProviderCredentialEntity>> activeCredentialByProviderIdCache;
    private final LoadingCache<Long, List<ModelApiBindingEntity>> apiBindingsByModelIdCache;
    private final LoadingCache<ResourceKey, Optional<TrafficPolicies>> trafficPoliciesCache;
    private final Cache<BillingRuleKey, Optional<BillingRuleEntity>> billingRuleCache;

    /** 计费规则回退查询（显式绑定码 → 模型族）走热路径，不经过缓存包装，故持有 mapper */
    private final BillingRuleMapper billingRuleMapper;

    /**
     * Constructs a new instance.
     */
    public GatewayDataService(AppCredentialMapper appCredentialMapper,
                              AppMapper appMapper,
                              RoutingConsumerMapper routingConsumerMapper,
                              RoutingRuleMapper routingRuleMapper,
                              RoutingRuleTargetMapper routingRuleTargetMapper,
                              ModelMapper modelMapper,
                              ModelPoolMapper modelPoolMapper,
                              ProviderAccountMapper providerAccountMapper,
                              ProviderCredentialMapper providerCredentialMapper,
                              ModelApiBindingMapper modelApiBindingMapper,
                              ResourceTrafficPolicyMapper resourceTrafficPolicyMapper,
                              RateLimitPolicyMapper rateLimitPolicyMapper,
                              QuotaPolicyMapper quotaPolicyMapper,
                              BillingRuleMapper billingRuleMapper) {
        this.appCredentialByKeyCache = optionalCache(appCredentialMapper::getByAppKey);
        this.appByIdCache = optionalCache(appMapper::getById);
        this.consumerBySecretKeyCache = optionalCache(routingConsumerMapper::getBySecretKey);
        this.enabledRulesByConsumerIdCache = listCache(routingRuleMapper::listEnabledByConsumerId);
        this.enabledRuleByConsumerAndCodeCache = optionalCache(key ->
                routingRuleMapper.getEnabledByConsumerIdAndRuleCode(key.consumerId(), key.ruleCode()));
        this.targetsByRuleIdCache = listCache(routingRuleTargetMapper::listByRuleId);
        this.modelByIdCache = optionalCache(modelMapper::getById);
        this.modelByCodeCache = optionalCache(modelMapper::getByCode);
        this.modelPoolByIdCache = optionalCache(modelPoolMapper::getById);
        this.modelPoolItemsByPoolCodeCache = listCache(modelPoolMapper::listItemsByPoolCode);
        this.providerByIdCache = optionalCache(providerAccountMapper::getById);
        this.providerByCodeCache = optionalCache(providerAccountMapper::getByCode);
        this.activeCredentialByProviderIdCache = optionalCache(providerCredentialMapper::getActiveByProviderId);
        this.apiBindingsByModelIdCache = listCache(modelApiBindingMapper::listByModelId);
        this.trafficPoliciesCache = optionalCache(key ->
                loadTrafficPolicies(key, resourceTrafficPolicyMapper, rateLimitPolicyMapper, quotaPolicyMapper));
        this.billingRuleMapper = billingRuleMapper;
        this.billingRuleCache = billingRuleCache();
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public AppCredentialEntity getAppCredentialByKey(String appKey) {
        return appCredentialByKeyCache.get(appKey).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public AppEntity getAppById(Long id) {
        return appByIdCache.get(id).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public RoutingConsumerEntity getConsumerBySecretKey(String secretKey) {
        return consumerBySecretKeyCache.get(secretKey).orElse(null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<RoutingRuleEntity> listEnabledRulesByConsumerId(Long consumerId) {
        return enabledRulesByConsumerIdCache.get(consumerId);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public RoutingRuleEntity getEnabledRuleByConsumerAndCode(Long consumerId, String ruleCode) {
        return enabledRuleByConsumerAndCodeCache.get(new ConsumerRuleKey(consumerId, ruleCode)).orElse(null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<RoutingRuleTargetEntity> listTargetsByRuleId(Long ruleId) {
        return targetsByRuleIdCache.get(ruleId);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModelEntity getModelById(Long id) {
        return modelByIdCache.get(id).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModelEntity getModelByCode(String modelCode) {
        if (modelCode == null) {
            return null;
        }
        return modelByCodeCache.get(modelCode).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModelPoolEntity getModelPoolById(Long id) {
        return modelPoolByIdCache.get(id).orElse(null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<ModelPoolItemEntity> listModelPoolItemsByPoolCode(String poolCode) {
        return modelPoolItemsByPoolCodeCache.get(poolCode);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ProviderAccountEntity getProviderById(Long id) {
        return providerByIdCache.get(id).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ProviderAccountEntity getProviderAccountByCode(String code) {
        if (code == null) {
            return null;
        }
        return providerByCodeCache.get(code).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ProviderCredentialEntity getActiveCredentialByProviderId(Long providerId) {
        return activeCredentialByProviderIdCache.get(providerId).orElse(null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<ModelApiBindingEntity> listApiBindingsByModelId(Long modelId) {
        return apiBindingsByModelIdCache.get(modelId);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public TrafficPolicies loadTrafficPolicies(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return new TrafficPolicies(null, null);
        }
        return trafficPoliciesCache.get(new ResourceKey(resourceType, resourceId))
                .orElse(new TrafficPolicies(null, null));
    }

    /**
     * 计费规则解析：先按模型显式绑定的规则业务码，未命中或未绑定时按模型族回退（族精确 → 默认规则）。
     *
     * @param ruleCode        模型显式绑定的计费规则业务码，可为空
     * @param modelFamilyCode 模型族 code（派生自绑定统一模型的 model_family），可为空
     */
    public BillingRuleEntity getActiveBillingRule(String ruleCode, String modelFamilyCode) {
        BillingRuleKey key = new BillingRuleKey(normalize(ruleCode), normalize(modelFamilyCode));
        Optional<BillingRuleEntity> cached = billingRuleCache.getIfPresent(key);
        if (cached != null) {
            return cached.orElse(null);
        }
        Optional<BillingRuleEntity> loaded = Optional.ofNullable(loadBillingRule(key));
        billingRuleCache.put(key, loaded);
        return loaded.orElse(null);
    }

    private BillingRuleEntity loadBillingRule(BillingRuleKey key) {
        LocalDateTime now = LocalDateTime.now();
        if (key.ruleCode() != null) {
            BillingRuleEntity byCode = billingRuleMapper.getActiveByRuleCode(key.ruleCode(), now);
            if (byCode != null) {
                return byCode;
            }
        }
        return billingRuleMapper.getActiveByModelFamily(key.modelFamilyCode(), now);
    }

    /**
     * 计费规则缓存：命中与未命中用不同过期时长，避免「刚绑定规则却长时间不出计费」。
     */
    private Cache<BillingRuleKey, Optional<BillingRuleEntity>> billingRuleCache() {
        long hitNanos = BILLING_RULE_HIT_TTL.toNanos();
        long missNanos = BILLING_RULE_MISS_TTL.toNanos();
        return Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfter(new Expiry<BillingRuleKey, Optional<BillingRuleEntity>>() {
                    @Override
                    public long expireAfterCreate(BillingRuleKey key,
                                                  Optional<BillingRuleEntity> value,
                                                  long currentTime) {
                        return value.isPresent() ? hitNanos : missNanos;
                    }

                    @Override
                    public long expireAfterUpdate(BillingRuleKey key,
                                                  Optional<BillingRuleEntity> value,
                                                  long currentTime,
                                                  long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(BillingRuleKey key,
                                                Optional<BillingRuleEntity> value,
                                                long currentTime,
                                                long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private TrafficPolicies loadTrafficPolicies(ResourceKey key,
                                                ResourceTrafficPolicyMapper resourceTrafficPolicyMapper,
                                                RateLimitPolicyMapper rateLimitPolicyMapper,
                                                QuotaPolicyMapper quotaPolicyMapper) {
        ResourceTrafficPolicyEntity binding = resourceTrafficPolicyMapper.getByResource(key.resourceType(), key.resourceId());
        if (binding == null) {
            return new TrafficPolicies(null, null);
        }
        RateLimitPolicyEntity rate = binding.getRateLimitPolicyId() == null
                ? null
                : rateLimitPolicyMapper.getById(binding.getRateLimitPolicyId());
        QuotaPolicyEntity quota = binding.getQuotaPolicyId() == null
                ? null
                : quotaPolicyMapper.getById(binding.getQuotaPolicyId());
        return new TrafficPolicies(
                ResourceTrafficPolicySupport.toRateLimitModel(rate),
                ResourceTrafficPolicySupport.toQuotaModel(quota)
        );
    }

    private static <K, V> LoadingCache<K, Optional<V>> optionalCache(Function<K, V> loader) {
        return builder().build(key -> Optional.ofNullable(loader.apply(key)));
    }

    private static <K, V> LoadingCache<K, List<V>> listCache(Function<K, List<V>> loader) {
        return builder().build(key -> {
            List<V> values = loader.apply(key);
            return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
        });
    }

    private static Caffeine<Object, Object> builder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(EXPIRE_AFTER_WRITE)
                .refreshAfterWrite(REFRESH_AFTER_WRITE);
    }

    private record ConsumerRuleKey(Long consumerId, String ruleCode) {
    }

    private record ResourceKey(String resourceType, Long resourceId) {
    }

    /** 计费规则缓存键：显式绑定码 + 模型族（两者都可为空） */
    private record BillingRuleKey(String ruleCode, String modelFamilyCode) {
    }
}
