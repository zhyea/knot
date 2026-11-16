package org.chobit.knot.gateway.config;

import java.util.List;

/**
 * 网关配置缓存域：一个域代表「一组配置表 + 受它们影响的本地缓存」。
 *
 * <p>域是失效的最小单位。不按缓存条目精确失效，是因为 {@code consumerBySecretKeyCache} /
 * {@code enabledRuleByConsumerAndCodeCache} 的 key 来自请求（任意 {@code sk-*}、任意 {@code Rule} 头），
 * 无法枚举；且改一张表往往连带影响同域多个缓存（池成员、平台凭据）。
 * 配置量级是百级，整域清空后的一次回源代价远小于「漏失效」。</p>
 *
 * <p>⚠ {@code tables()} 与 {@code ConfigRevisionMapper.xml} 的表清单必须完全一致，
 * 由 {@code ConfigCacheDomainTest} 守门——漏一张表会让该域永远探测不到变更，且没有任何运行时症状。
 * 表名顺序不影响判定。</p>
 */
public enum ConfigCacheDomain {

    APP("kb_apps"),

    CONSUMER("kb_routing_consumers"),

    ROUTING_RULE("kb_routing_rules"),

    ROUTING_TARGET("kb_routing_rule_targets"),

    MODEL("kb_models"),

    MODEL_POOL("kb_model_pools", "kb_model_pool_items"),

    PROVIDER("kb_provider_accounts", "kb_provider_credentials"),

    MODEL_API_BINDING("kb_model_api_bindings"),

    TRAFFIC_POLICY("kb_resource_traffic_policies", "kb_rate_limit_policies",
            "kb_quota_policies", "kb_resource_quota_bindings"),

    BILLING_RULE("kb_billing_rules", "kb_billing_rule_versions"),

    /** 词库资源副本由 {@code ModerationWordResource} 持有，不在 {@code GatewayConfigCacheService} 内。 */
    MODERATION_WORD("kb_moderation_word_lists");

    private final List<String> tables;

    ConfigCacheDomain(String... tables) {
        this.tables = List.of(tables);
    }

    /**
     * 本域依赖的配置表。
     */
    public List<String> tables() {
        return tables;
    }
}
