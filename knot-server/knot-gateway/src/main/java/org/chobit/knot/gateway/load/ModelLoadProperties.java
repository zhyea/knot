package org.chobit.knot.gateway.load;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 最空闲选模所需的在途并发计量配置（{@code knot.redis.load.*}）。
 *
 * <p>{@code store} 决定用哪种存储实现：单节点 {@code caffeine}（默认，节点内近似），
 * 多节点 {@code redis}（全局口径）。两种实现共用同一套键，切换只改这一项配置。</p>
 *
 * <p><b>故障口径：redis 档一律 fail-hard</b>，无 fail-open 开关、无降级档
 * （2026-10-15 决策）——在途数是 {@code LEAST_LOAD} 选路的唯一数据源，
 * 静默按 0 放行会让所有模型被看成「不拥塞」，已过载的模型继续被加压。
 * Redis 不可用时抛异常 → 网关 503 + ERROR 报警。</p>
 */
@ConfigurationProperties(prefix = "knot.redis.load")
public class ModelLoadProperties {

    /**
     * 计量存储实现：{@code caffeine}（单节点，默认）/ {@code redis}（多节点全局）。
     *
     * <p>⚠ <b>必须保持 {@code null} 默认值</b>：null 表示「未显式设置」，
     * 归一时才回落旧键 {@code knot.load.store}；给了默认值就分不清显式配置与未配置。</p>
     */
    private String store;

    /**
     * 存储键前缀：多个环境共用同一 Redis 时用于隔离。
     */
    private String keyPrefix = "knot:load";

    /**
     * 在途计数的存活时间（秒）：既防「计数泄漏永久化」，也是条目在无新请求时的回收时间。
     * 必须显著大于单请求的最长耗时（含长流式响应），否则正常请求也会被当作泄漏清掉。
     */
    private long ttlSeconds = 300L;

    /**
     * Caffeine 存储的最大条目数，超过后按 LRU 淘汰。
     */
    private long maximumSize = 20_000L;

    /**
     * Returns the requested value.
     */
    public String getStore() {
        return store;
    }

    /**
     * Sets the provided value.
     */
    public void setStore(String store) {
        this.store = store;
    }

    /**
     * Returns the requested value.
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Sets the provided value.
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * Returns the requested value.
     */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * Sets the provided value.
     */
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Returns the requested value.
     */
    public long getMaximumSize() {
        return maximumSize;
    }

    /**
     * Sets the provided value.
     */
    public void setMaximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
    }
}
