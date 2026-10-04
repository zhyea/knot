package org.chobit.knot.gateway.traffic;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 频控 / 额度计数键的拼装入口。
 *
 * <p>键格式：{@code <prefix>:<维度族>:<资源类型>:<资源ID>:<维度>:<窗口>}，例如：</p>
 * <pre>
 * knot:traffic:rl:MODEL:12:SECOND:1760000000
 * knot:traffic:qt:APP:3:DAILY_REQUESTS:2026-10-04
 * knot:traffic:qt:MODEL:12:TOTAL_TOKENS:ALL
 * </pre>
 *
 * <p>键里带上窗口标识，计数条目随窗口自然过期，不需要后台清理任务。</p>
 */
public final class TrafficCounterKeys {

    private static final String RATE_LIMIT_PREFIX = "rl";
    private static final String QUOTA_PREFIX = "qt";

    private final String prefix;
    private final ZoneId zone;

    /**
     * Constructs a new instance.
     *
     * @param prefix 键前缀，多环境共用同一 Redis 时用于隔离
     * @param zone   日 / 月窗口换算所用时区
     */
    public TrafficCounterKeys(String prefix, ZoneId zone) {
        this.prefix = prefix;
        this.zone = zone;
    }

    /**
     * 频控计数键：窗口由 epoch 毫秒整除得出，与时区无关。
     */
    public TrafficCounterKey rateLimit(String resourceType, long resourceId, RateLimitWindow window, long nowMillis) {
        String key = prefix + ':' + RATE_LIMIT_PREFIX + ':' + resourceType + ':' + resourceId
                + ':' + window.code() + ':' + window.windowId(nowMillis);
        return new TrafficCounterKey(key, window.expireAtMillis(nowMillis));
    }

    /**
     * 额度计数键：日 / 月窗口按配置时区换算。
     */
    public TrafficCounterKey quota(String resourceType, long resourceId, QuotaDimension dimension, Instant now) {
        ZonedDateTime zoned = now.atZone(zone);
        String key = prefix + ':' + QUOTA_PREFIX + ':' + resourceType + ':' + resourceId
                + ':' + dimension.code() + ':' + dimension.windowId(zoned);
        return new TrafficCounterKey(key, dimension.expireAtMillis(zoned));
    }
}
