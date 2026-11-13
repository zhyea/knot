package org.chobit.knot.gateway.store;

/**
 * 业务域运行时存储的实现模式（只有两档），配置键为 {@code knot.<domain>.store}。
 * {@link #CAFFEINE}：进程内视图，不依赖 Redis，单节点默认档；{@link #REDIS}：多节点共享全局视图，Redis 不可用时 fail-hard（抛异常 → 网关 503 + ERROR 日志），不降级到本地缓存。
 * 摘除状态（outage）不在本词表内，一律走 Caffeine（{@code TargetOutageStore} 只有这一个实现）。
 */
public enum StoreMode {

    /**
     * 进程内 Caffeine：单节点视图，默认档，不依赖 Redis。
     */
    CAFFEINE("caffeine"),

    /**
     * Redis：多节点共享全局视图；Redis 不可用时 fail-hard，抛异常并打 ERROR 日志。
     */
    REDIS("redis");

    private final String code;

    StoreMode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * 归一化配置值（去空白 + 小写）为模式；无法识别时返回 {@code null}
     * 由调用方决定报错或回落默认——「配了怪值」与「没配」必须可区分。
     */
    public static StoreMode fromConfigured(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        for (StoreMode mode : values()) {
            if (mode.code.equals(normalized)) {
                return mode;
            }
        }
        return null;
    }
}
