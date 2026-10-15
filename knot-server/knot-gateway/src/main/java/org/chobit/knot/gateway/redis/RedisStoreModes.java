package org.chobit.knot.gateway.redis;

/**
 * 网关运行时存储的实现模式（<b>只有两档</b>）。
 *
 * <p>模式名即 {@code knot.redis.<domain>.store} 的取值域，对应
 * 「单节点用进程内 Caffeine / 多节点用 Redis」这条选型：</p>
 * <ul>
 *   <li>{@link #CAFFEINE}：进程内视图，不依赖 Redis。单节点默认档。</li>
 *   <li>{@link #REDIS}：多节点共享全局视图。<b>Redis 不可用时一律 fail-hard</b>
 *       ——抛 {@link RedisUnavailableException} → 网关 503 + ERROR 日志报警，
 *       不静默降级到本地缓存（降级会让频控超发、负载失衡，属不可接受的静默损失）。</li>
 * </ul>
 *
 * <p>⚠ 摘除状态（outage）<b>不在本词表内</b>：它是纯本地统计，一律走 Caffeine
 * （{@code TargetOutageStore} 只有 Caffeine 一个实现），因此没有多节点档位。
 * 摘除是「本节点自己的异常观察」，不需要跨节点共享；放 Redis 反而让 Redis 故障
 * 直接打掉网关的自我保护能力。</p>
 */
public final class RedisStoreModes {

    /**
     * 进程内 Caffeine：单节点视图，默认档，不依赖 Redis。
     */
    public static final String CAFFEINE = "caffeine";

    /**
     * Redis：多节点共享全局视图；Redis 不可用时 fail-hard，抛异常并打 ERROR 日志。
     */
    public static final String REDIS = "redis";

    private RedisStoreModes() {
    }

    /**
     * 归一化模式名（去空白 + 小写）；无法识别时返回 {@code null} 由调用方决定报错或回退。
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase();
    }

    /**
     * 是否为合法模式值。
     */
    public static boolean valid(String normalized) {
        return CAFFEINE.equals(normalized) || REDIS.equals(normalized);
    }
}