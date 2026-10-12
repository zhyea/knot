package org.chobit.knot.gateway.load;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 最空闲选模所需的在途并发计量配置。
 *
 * <p>{@code store} 决定用哪种存储实现：单节点 {@code caffeine}（默认，节点内近似），
 * 多节点 {@code redis}（全局口径）。两种实现共用同一套键，切换只改这一项配置。</p>
 */
@ConfigurationProperties(prefix = "knot.load")
public class ModelLoadProperties {

    /**
     * 计量存储实现：{@code caffeine}（单节点，默认）或 {@code redis}（多节点共享）。
     */
    private String store = "caffeine";

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
     * Redis 不可用时是否放行：{@code true} 记日志后按「在途为 0」处理，{@code false} 直接抛错。
     */
    private boolean failOpen = true;

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

    /**
     * Returns the requested value.
     */
    public boolean isFailOpen() {
        return failOpen;
    }

    /**
     * Sets the provided value.
     */
    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
}
