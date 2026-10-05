package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.constants.enums.QuotaWindowEnum;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 限流 / 限额计数键的拼装入口。
 *
 * <p>键格式：{@code <prefix>:<维度族>:<资源类型>:<资源ID>:<维度>[:<币种>]:<窗口>}，例如：</p>
 * <pre>
 * knot:traffic:rl:MODEL:12:RPM:29333333
 * knot:traffic:rl:MODEL:12:TPM:29333333
 * knot:traffic:qt:APP:3:TOKENS:202610
 * knot:traffic:qt:APP:3:COST:USD:20261005
 * </pre>
 *
 * <p>限流键用 epoch 分钟序号（与时区无关），限额键用按时区换算的窗口标识
 * （{@code 202610} 表示 2026 年 10 月，{@code 20261005} 表示 10 月 5 日当天）。
 * 换窗口即换键、旧键随 TTL 过期，因此两类键都不需要后台清理任务。</p>
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
     * @param zone   限额窗口换算所用时区；多节点必须一致
     */
    public TrafficCounterKeys(String prefix, ZoneId zone) {
        this.prefix = prefix;
        this.zone = zone;
    }

    /**
     * 限流计数键：分钟级固定窗口，由 epoch 毫秒整除得出。
     */
    public TrafficCounterKey rateLimit(String resourceType,
                                       long resourceId,
                                       RateLimitDimension dimension,
                                       long nowMillis) {
        String key = prefix + ':' + RATE_LIMIT_PREFIX + ':' + resourceType + ':' + resourceId
                + ':' + dimension.code() + ':' + dimension.windowId(nowMillis);
        return new TrafficCounterKey(key, dimension.expireAtMillis(nowMillis));
    }

    /**
     * 限额计数键：窗口由策略决定，窗口一过自动清零。
     *
     * @param currency 仅成本维度需要，把不同币种分到不同键上
     */
    public TrafficCounterKey quota(String resourceType,
                                   long resourceId,
                                   QuotaDimension dimension,
                                   QuotaWindowEnum window,
                                   String currency,
                                   Instant now) {
        ZonedDateTime zoned = now.atZone(zone);
        String currencyPart = dimension == QuotaDimension.COST && currency != null
                ? ':' + currency.trim().toUpperCase()
                : "";
        String key = prefix + ':' + QUOTA_PREFIX + ':' + resourceType + ':' + resourceId
                + ':' + dimension.code() + currencyPart + ':' + window.windowId(zoned);
        return new TrafficCounterKey(key, window.expireAtMillis(zoned));
    }
}
