package org.chobit.knot.gateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.svc.service.GatewayConfigQueryService;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.chobit.knot.svc.vo.gateway.AppConfigVo;
import org.chobit.knot.svc.vo.gateway.BillingRuleConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelPoolConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelPoolItemConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderAccountConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderCredentialConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingConsumerConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingRuleConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingRuleTargetConfigVo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;


/**
 * 网关配置缓存：Caffeine 本地缓存的唯一实例，数据来自 {@link GatewayConfigQueryService}；不注入 Mapper，也不 import 任何 {@code dal.entity}。
 * 本类 TTL 是网关配置生效延迟的唯一上限：{@code expireAfterWrite} 硬过期 + {@code refreshAfterWrite} 后台刷新。
 */
@Service
public class GatewayConfigCacheService {

    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMinutes(15);
    private static final Duration REFRESH_AFTER_WRITE = Duration.ofSeconds(30);

    /**
     * 单实例内的条目上限：TTL 只限制单条活多久，不限制窗口内能塞多少条。
     * 键来自请求的缓存（{@code consumerBySecretKeyCache}、{@code enabledRuleByConsumerAndCodeCache}）靠它兜底。
     */
    private static final long MAX_CACHE_ENTRIES = 10_000L;

    /**
     * 命中计费规则的缓存时长：与其余缓存一致。
     */
    private static final Duration BILLING_RULE_HIT_TTL = Duration.ofMinutes(10);

    /**
     * 未命中计费规则的缓存时长：显著短于命中时长，空结果只做防穿透。
     */
    private static final Duration BILLING_RULE_MISS_TTL = Duration.ofSeconds(30);

    private final GatewayConfigQueryService configQuery;

    /**
     * {@code refreshAfterWrite} 的异步重载执行器（{@code GatewayAsyncConfiguration#gatewayConfigRefreshExecutor()}）。
     * loader 全是 MyBatis 阻塞 JDBC，必须给阻塞专用的小池，不能落到 {@code ForkJoinPool.commonPool()}。
     */
    private final Executor refreshExecutor;

    private final LoadingCache<Long, Optional<AppConfigVo>> appByIdCache;
    private final LoadingCache<String, Optional<RoutingConsumerConfigVo>> consumerBySecretKeyCache;
    private final LoadingCache<ConsumerRuleKey, Optional<RoutingRuleConfigVo>> enabledRuleByConsumerAndCodeCache;
    private final LoadingCache<String, List<RoutingRuleTargetConfigVo>> targetsByRuleIdCache;
    private final LoadingCache<Long, Optional<ModelConfigVo>> modelByIdCache;
    private final LoadingCache<String, Optional<ModelConfigVo>> modelByCodeCache;
    private final LoadingCache<Long, Optional<ModelPoolConfigVo>> modelPoolByIdCache;
    private final LoadingCache<String, Optional<ModelPoolConfigVo>> modelPoolByCodeCache;
    private final LoadingCache<String, List<ModelPoolItemConfigVo>> modelPoolItemsByPoolCodeCache;
    private final LoadingCache<Long, Optional<ProviderAccountConfigVo>> providerByIdCache;
    private final LoadingCache<String, Optional<ProviderAccountConfigVo>> providerByCodeCache;
    private final LoadingCache<Long, Optional<ProviderCredentialConfigVo>> activeCredentialByProviderIdCache;
    private final LoadingCache<Long, List<ModelApiBindingConfigVo>> apiBindingsByModelIdCache;
    private final LoadingCache<ResourceKey, Optional<TrafficPolicies>> trafficPoliciesCache;
    private final Cache<BillingRuleKey, Optional<BillingRuleConfigVo>> billingRuleCache;

    public GatewayConfigCacheService(GatewayConfigQueryService configQuery,
                                     ResourceTrafficPolicySupport trafficPolicySupport,
                                     @Qualifier("gatewayConfigRefreshExecutor") Executor refreshExecutor) {
        this.configQuery = configQuery;
        this.refreshExecutor = Objects.requireNonNull(refreshExecutor, "refreshExecutor");
        this.appByIdCache = optionalCache(configQuery::getAppById);
        this.consumerBySecretKeyCache = optionalCache(configQuery::getConsumerBySecretKey);
        this.enabledRuleByConsumerAndCodeCache = optionalCache(key ->
                configQuery.getEnabledRuleByConsumerCodeAndRuleCode(key.consumerCode(), key.ruleCode()));
        this.targetsByRuleIdCache = listCache(configQuery::listTargetsByRuleCode);
        this.modelByIdCache = optionalCache(configQuery::getModelById);
        this.modelByCodeCache = optionalCache(configQuery::getModelByCode);
        this.modelPoolByIdCache = optionalCache(configQuery::getModelPoolById);
        this.modelPoolByCodeCache = optionalCache(configQuery::getModelPoolByCode);
        this.modelPoolItemsByPoolCodeCache = listCache(configQuery::listModelPoolItemsByPoolCode);
        this.providerByIdCache = optionalCache(configQuery::getProviderById);
        this.providerByCodeCache = optionalCache(configQuery::getProviderByCode);
        this.activeCredentialByProviderIdCache = optionalCache(configQuery::getActiveCredentialByProviderId);
        this.apiBindingsByModelIdCache = listCache(configQuery::listApiBindingsByModelId);
        this.trafficPoliciesCache = optionalCache(key ->
                trafficPolicySupport.load(key.resourceType(), key.resourceId()));
        this.billingRuleCache = billingRuleCache();
    }

    public AppConfigVo getAppById(Long id) {
        return appByIdCache.get(id).orElse(null);
    }

    public RoutingConsumerConfigVo getConsumerBySecretKey(String secretKey) {
        return consumerBySecretKeyCache.get(secretKey).orElse(null);
    }

    public RoutingRuleConfigVo getEnabledRuleByConsumerCodeAndCode(String consumerCode, String ruleCode) {
        return enabledRuleByConsumerAndCodeCache.get(new ConsumerRuleKey(consumerCode, ruleCode)).orElse(null);
    }

    public List<RoutingRuleTargetConfigVo> listTargetsByRuleCode(String ruleCode) {
        return targetsByRuleIdCache.get(ruleCode);
    }

    public ModelConfigVo getModelById(Long id) {
        if (id == null) {
            return null;
        }
        return modelByIdCache.get(id).orElse(null);
    }

    public ModelConfigVo getModelByCode(String modelCode) {
        if (modelCode == null) {
            return null;
        }
        return modelByCodeCache.get(modelCode).orElse(null);
    }

    public ModelPoolConfigVo getModelPoolById(Long id) {
        if (id == null) {
            return null;
        }
        return modelPoolByIdCache.get(id).orElse(null);
    }

    /**
     * 按存储主键 code 取模型池。对齐路由目标「存 code 不存 id」口径，且对 null 入参安全
     * （Caffeine {@code get(null)} 会抛 NPE）。
     */
    public ModelPoolConfigVo getModelPoolByCode(String poolCode) {
        if (poolCode == null) {
            return null;
        }
        return modelPoolByCodeCache.get(poolCode).orElse(null);
    }

    public List<ModelPoolItemConfigVo> listModelPoolItemsByPoolCode(String poolCode) {
        return modelPoolItemsByPoolCodeCache.get(poolCode);
    }

    public ProviderAccountConfigVo getProviderById(Long id) {
        return providerByIdCache.get(id).orElse(null);
    }

    public ProviderAccountConfigVo getProviderAccountByCode(String code) {
        if (code == null) {
            return null;
        }
        return providerByCodeCache.get(code).orElse(null);
    }

    public ProviderCredentialConfigVo getActiveCredentialByProviderId(Long providerId) {
        return activeCredentialByProviderIdCache.get(providerId).orElse(null);
    }

    public List<ModelApiBindingConfigVo> listApiBindingsByModelId(Long modelId) {
        return apiBindingsByModelIdCache.get(modelId);
    }

    public TrafficPolicies loadTrafficPolicies(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return new TrafficPolicies(null, List.of());
        }
        return trafficPoliciesCache.get(new ResourceKey(resourceType, resourceId))
                .orElse(new TrafficPolicies(null, List.of()));
    }

    /**
     * 计费规则解析：先按模型显式绑定的规则业务码，未命中或未绑定时按模型族回退（族精确 → 默认规则）。
     * 解析本身在 {@link GatewayConfigQueryService#getActiveBillingRule}；本方法只负责缓存：命中 10 分钟、未命中只挡 30 秒穿透。
     *
     * @param ruleCode        模型显式绑定的计费规则业务码，可为空
     * @param modelFamilyCode 模型族 code（派生自绑定统一模型的 model_family），可为空
     */
    public BillingRuleConfigVo getActiveBillingRule(String ruleCode, String modelFamilyCode) {
        BillingRuleKey key = new BillingRuleKey(normalize(ruleCode), normalize(modelFamilyCode));
        Optional<BillingRuleConfigVo> cached = billingRuleCache.getIfPresent(key);
        if (cached != null) {
            return cached.orElse(null);
        }
        Optional<BillingRuleConfigVo> loaded =
                Optional.ofNullable(configQuery.getActiveBillingRule(key.ruleCode(), key.modelFamilyCode()));
        billingRuleCache.put(key, loaded);
        return loaded.orElse(null);
    }

    /**
     * 计费规则缓存：命中与未命中使用不同的过期时长，由 {@link Expiry} 按条目取值决定。
     */
    private Cache<BillingRuleKey, Optional<BillingRuleConfigVo>> billingRuleCache() {
        long hitNanos = BILLING_RULE_HIT_TTL.toNanos();
        long missNanos = BILLING_RULE_MISS_TTL.toNanos();
        return Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfter(new Expiry<BillingRuleKey, Optional<BillingRuleConfigVo>>() {
                    @Override
                    public long expireAfterCreate(BillingRuleKey key,
                                                  Optional<BillingRuleConfigVo> value,
                                                  long currentTime) {
                        return value.isPresent() ? hitNanos : missNanos;
                    }

                    @Override
                    public long expireAfterUpdate(BillingRuleKey key,
                                                  Optional<BillingRuleConfigVo> value,
                                                  long currentTime,
                                                  long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(BillingRuleKey key,
                                                Optional<BillingRuleConfigVo> value,
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

    private <K, V> LoadingCache<K, Optional<V>> optionalCache(Function<K, V> loader) {
        return builder().build(key -> Optional.ofNullable(loader.apply(key)));
    }

    /**
     * 列表缓存：返回不可变列表（{@code List.copyOf}），元素是缓存里的共享实例。
     */
    private <K, V> LoadingCache<K, List<V>> listCache(Function<K, List<V>> loader) {
        return builder().build(key -> {
            List<V> values = loader.apply(key);
            return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
        });
    }

    /**
     * 所有 {@link LoadingCache} 的共同配方：容量上限 + 写入后过期 + 30 秒后台刷新 + 阻塞专用 {@code executor}。
     */
    private Caffeine<Object, Object> builder() {
        return Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterWrite(EXPIRE_AFTER_WRITE)
                .refreshAfterWrite(REFRESH_AFTER_WRITE)
                .executor(refreshExecutor);
    }

    private record ConsumerRuleKey(String consumerCode, String ruleCode) {
    }

    private record ResourceKey(String resourceType, Long resourceId) {
    }

    /** 计费规则缓存键：显式绑定码 + 模型族（两者都可为空） */
    private record BillingRuleKey(String ruleCode, String modelFamilyCode) {
    }
}
