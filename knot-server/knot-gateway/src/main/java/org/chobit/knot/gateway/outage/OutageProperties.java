package org.chobit.knot.gateway.outage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异常摘除的运行时存储配置。
 *
 * <p>{@code store} 决定用哪种实现：单节点 {@code caffeine}（默认，节点内视图），
 * 多节点 {@code redis}（全局视图）。两种实现共用同一套键与同一套状态语义，切换只改这一项。</p>
 */
@ConfigurationProperties(prefix = "knot.outage")
public class OutageProperties {

    /**
     * 存储实现：{@code caffeine}（单节点，默认）或 {@code redis}（多节点共享）。
     *
     * <p>{@code fallback}（多节点推荐）：Redis 优先，不可用时自动降级 Caffeine，恢复后回灌并切回。</p>
     */
    private String store = "caffeine";

    /**
     * 存储键前缀：多个环境共用同一 Redis 时用于隔离。
     */
    private String keyPrefix = "knot:outage";

    /**
     * {@code fallback} 档的恢复探测间隔（毫秒）：降级后每隔这么久尝试一次 Redis 探活。
     * 太小会把「Redis 慢」放大成正反馈。
     */
    private long recoverProbeIntervalMs = 10_000L;

    /**
     * 条目的自然回收宽限（毫秒）：追加在「窗口 / 摘除 / 探活中最晚的到期时刻」之后，
     * 防止边界处刚过期就被读成有效状态。
     */
    private long expireGraceMs = 60_000L;

    /**
     * Caffeine 存储的最大条目数，超过后按 LRU 淘汰。
     */
    private long maximumSize = 20_000L;

    /**
     * Redis 不可用时是否放行：{@code true} 记日志后按「健康」处理（不摘除）。
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
    public long getRecoverProbeIntervalMs() {
        return recoverProbeIntervalMs;
    }

    /**
     * Sets the provided value.
     */
    public void setRecoverProbeIntervalMs(long recoverProbeIntervalMs) {
        this.recoverProbeIntervalMs = recoverProbeIntervalMs;
    }

    /**
     * Returns the requested value.
     */
    public long getExpireGraceMs() {
        return expireGraceMs;
    }

    /**
     * Sets the provided value.
     */
    public void setExpireGraceMs(long expireGraceMs) {
        this.expireGraceMs = expireGraceMs;
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
