package org.chobit.knot.gateway.traffic;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * 频控 / 额度计数配置。
 *
 * <p>{@code store} 决定用哪种存储实现：单节点 {@code caffeine}（默认），
 * 多节点 {@code redis}。两种实现共用同一套键与判定逻辑，切换只改这一项配置。</p>
 */
@ConfigurationProperties(prefix = "knot.traffic")
public class TrafficCounterProperties {

    /**
     * 计数存储实现：{@code caffeine}（单节点，默认）或 {@code redis}（多节点共享）。
     */
    private String store = "caffeine";

    /**
     * 存储键前缀：多个环境共用同一 Redis 时用于隔离。
     */
    private String keyPrefix = "knot:traffic";

    /**
     * 日 / 月额度窗口换算所用时区；多节点必须配置成同一个值，否则窗口边界不一致。
     */
    private String zone = "Asia/Shanghai";

    /**
     * Caffeine 存储的最大条目数，超过后按 LRU 淘汰。
     */
    private long maximumSize = 200_000L;

    /**
     * Redis 不可用时是否放行：{@code true} 记日志后按未超处理，{@code false} 直接抛错。
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
    public String getZone() {
        return zone;
    }

    /**
     * Sets the provided value.
     */
    public void setZone(String zone) {
        this.zone = zone;
    }

    /**
     * 时区配置对应的 {@link ZoneId}。
     */
    public ZoneId zoneId() {
        return ZoneId.of(zone);
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
