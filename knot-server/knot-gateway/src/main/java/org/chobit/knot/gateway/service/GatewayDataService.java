package org.chobit.knot.gateway.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.chobit.knot.gateway.entity.AppCredentialEntity;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ModelPoolEntity;
import org.chobit.knot.gateway.entity.ModelPoolItemEntity;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.entity.QuotaPolicyEntity;
import org.chobit.knot.gateway.entity.RateLimitPolicyEntity;
import org.chobit.knot.gateway.entity.ResourceTrafficPolicyEntity;
import org.chobit.knot.gateway.entity.RoutingConsumerEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.chobit.knot.gateway.entity.RoutingRuleTargetEntity;
import org.chobit.knot.gateway.mapper.AppCredentialMapper;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.ModelApiBindingMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.mapper.ModelPoolMapper;
import org.chobit.knot.gateway.mapper.ProviderCredentialMapper;
import org.chobit.knot.gateway.mapper.ProviderMapper;
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

    private final LoadingCache<String, Optional<AppCredentialEntity>> appCredentialByKeyCache;
    private final LoadingCache<Long, Optional<AppEntity>> appByIdCache;
    private final LoadingCache<String, Optional<RoutingConsumerEntity>> consumerBySecretKeyCache;
    private final LoadingCache<Long, List<RoutingRuleEntity>> enabledRulesByConsumerIdCache;
    private final LoadingCache<ConsumerRuleKey, Optional<RoutingRuleEntity>> enabledRuleByConsumerAndCodeCache;
    private final LoadingCache<Long, List<RoutingRuleTargetEntity>> targetsByRuleIdCache;
    private final LoadingCache<Long, Optional<ModelEntity>> modelByIdCache;
    private final LoadingCache<String, Optional<ModelEntity>> modelByCodeCache;
    private final LoadingCache<Long, Optional<ModelPoolEntity>> modelPoolByIdCache;
    private final LoadingCache<Long, List<ModelPoolItemEntity>> modelPoolItemsByPoolIdCache;
    private final LoadingCache<Long, Optional<ProviderEntity>> providerByIdCache;
    private final LoadingCache<Long, Optional<ProviderCredentialEntity>> activeCredentialByProviderIdCache;
    private final LoadingCache<Long, List<ModelApiBindingEntity>> apiBindingsByModelIdCache;
    private final LoadingCache<ResourceKey, Optional<TrafficPolicies>> trafficPoliciesCache;
    private final LoadingCache<Long, Optional<BillingRuleEntity>> activeBillingRuleByIdCache;

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
                              ProviderMapper providerMapper,
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
        this.modelByCodeCache = optionalCache(modelCode -> modelMapper.list(null, null).stream()
                .filter(model -> modelCode.equals(model.getModelCode()))
                .findFirst()
                .orElse(null));
        this.modelPoolByIdCache = optionalCache(modelPoolMapper::getById);
        this.modelPoolItemsByPoolIdCache = listCache(modelPoolMapper::listItemsByPoolId);
        this.providerByIdCache = optionalCache(providerMapper::getById);
        this.activeCredentialByProviderIdCache = optionalCache(providerCredentialMapper::getActiveByProviderId);
        this.apiBindingsByModelIdCache = listCache(modelApiBindingMapper::listByModelId);
        this.trafficPoliciesCache = optionalCache(key ->
                loadTrafficPolicies(key, resourceTrafficPolicyMapper, rateLimitPolicyMapper, quotaPolicyMapper));
        this.activeBillingRuleByIdCache = optionalCache(ruleId ->
                billingRuleMapper.getActiveByRuleId(ruleId, LocalDateTime.now()));
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
    public List<ModelPoolItemEntity> listModelPoolItemsByPoolId(Long poolId) {
        return modelPoolItemsByPoolIdCache.get(poolId);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ProviderEntity getProviderById(Long id) {
        return providerByIdCache.get(id).orElse(null);
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
     * Returns the requested value. Executes the public operation.
     */
    public BillingRuleEntity getActiveBillingRuleById(Long ruleId) {
        return activeBillingRuleByIdCache.get(ruleId).orElse(null);
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
}
