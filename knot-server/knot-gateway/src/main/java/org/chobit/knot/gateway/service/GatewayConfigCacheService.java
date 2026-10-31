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
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;


/**
 * 网关配置缓存：Caffeine 本地缓存的<b>唯一实例</b>，数据来自 {@link GatewayConfigQueryService}。
 *
 * <p>⭐ <b>为什么缓存留在这里、查询搬去 svc</b>：
 * 「读哪张表」是数据访问（§3.1 只能 svc）；「缓存多久、容量多大、什么时候预热」是
 * 「这个进程怎么跑」（§3.2 只有一个 app 用就留在该 app）。所以本类<b>一个 Mapper 都不注入</b>，
 * 也不再 import 任何 {@code dal.entity}——2026-10-31 之前它注入 9 个 Mapper、吐 11 种实体，
 * 连带 9 个下游为读字段跟着 import 实体，实体棘轮从 1 个长到 10 个而无人报警。</p>
 *
 * <p>⚠ <b>本类的 TTL 是网关配置生效延迟的唯一上限</b>：{@code expireAfterWrite=10min} +
 * {@code refreshAfterWrite=30s}。管理端改完配置，最迟 10 分钟才在网关生效——
 * 排查「改了没生效」先看这里，别先去怀疑库或 Mapper。</p>
 */
@Service
public class GatewayConfigCacheService {

    private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMinutes(10);
    private static final Duration REFRESH_AFTER_WRITE = Duration.ofSeconds(30);

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

    private final GatewayConfigQueryService configQuery;

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

    /**
     * Constructs a new instance.
     */
    public GatewayConfigCacheService(GatewayConfigQueryService configQuery,
                                     ResourceTrafficPolicySupport trafficPolicySupport) {
        this.configQuery = configQuery;
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

    /**
     * Returns the requested value. Executes the public operation.
     */
    public AppConfigVo getAppById(Long id) {
        return appByIdCache.get(id).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public RoutingConsumerConfigVo getConsumerBySecretKey(String secretKey) {
        return consumerBySecretKeyCache.get(secretKey).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public RoutingRuleConfigVo getEnabledRuleByConsumerCodeAndCode(String consumerCode, String ruleCode) {
        return enabledRuleByConsumerAndCodeCache.get(new ConsumerRuleKey(consumerCode, ruleCode)).orElse(null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<RoutingRuleTargetConfigVo> listTargetsByRuleCode(String ruleCode) {
        return targetsByRuleIdCache.get(ruleCode);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModelConfigVo getModelById(Long id) {
        if (id == null) {
            return null;
        }
        return modelByIdCache.get(id).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModelConfigVo getModelByCode(String modelCode) {
        if (modelCode == null) {
            return null;
        }
        return modelByCodeCache.get(modelCode).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
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

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<ModelPoolItemConfigVo> listModelPoolItemsByPoolCode(String poolCode) {
        return modelPoolItemsByPoolCodeCache.get(poolCode);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ProviderAccountConfigVo getProviderById(Long id) {
        return providerByIdCache.get(id).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ProviderAccountConfigVo getProviderAccountByCode(String code) {
        if (code == null) {
            return null;
        }
        return providerByCodeCache.get(code).orElse(null);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ProviderCredentialConfigVo getActiveCredentialByProviderId(Long providerId) {
        return activeCredentialByProviderIdCache.get(providerId).orElse(null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<ModelApiBindingConfigVo> listApiBindingsByModelId(Long modelId) {
        return apiBindingsByModelIdCache.get(modelId);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public TrafficPolicies loadTrafficPolicies(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return new TrafficPolicies(null, List.of());
        }
        return trafficPoliciesCache.get(new ResourceKey(resourceType, resourceId))
                .orElse(new TrafficPolicies(null, List.of()));
    }

    /**
     * 计费规则解析：先按模型显式绑定的规则业务码，未命中或未绑定时按模型族回退（族精确 → 默认规则）。
     *
     * <p>解析本身在 {@link GatewayConfigQueryService#getActiveBillingRule}（那是数据语义）；
     * 这里只负责「命中缓存 10 分钟、未命中只挡 30 秒穿透」。</p>
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
     * 计费规则缓存：命中与未命中用不同过期时长，避免「刚绑定规则却长时间不出计费」。
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

    private static <K, V> LoadingCache<K, Optional<V>> optionalCache(Function<K, V> loader) {
        return builder().build(key -> Optional.ofNullable(loader.apply(key)));
    }

    /**
     * 列表缓存：返回<b>不可变列表</b>，元素是缓存里的共享实例。
     *
     * <p>⭐ 2026-10-31 之后元素是 <b>record</b>（原来是可变实体）：不可变天然免疫
     * 「调用方就地改元素污染缓存」那类事故，所以不必再做深拷贝。这条不是优化取舍，
     * 是换了载体之后风险自己消失了。</p>
     */
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

    private record ConsumerRuleKey(String consumerCode, String ruleCode) {
    }

    private record ResourceKey(String resourceType, Long resourceId) {
    }

    /** 计费规则缓存键：显式绑定码 + 模型族（两者都可为空） */
    private record BillingRuleKey(String ruleCode, String modelFamilyCode) {
    }
}
