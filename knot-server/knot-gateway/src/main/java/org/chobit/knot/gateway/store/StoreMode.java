package org.chobit.knot.gateway.store;

/**
 * 业务域运行时存储的实现模式（<b>只有两档</b>）。
 *
 * <p>存储选型是 traffic / load 等<b>业务域自己的属性</b>（{@code knot.<domain>.store}），
 * 不是独立的「cache 层」——本枚举与归一处只是各域共用的选型词表：</p>
 * <ul>
 *   <li>{@link #CAFFEINE}：进程内视图，不依赖 Redis。单节点默认档。</li>
 *   <li>{@link #REDIS}：多节点共享全局视图。<b>Redis 不可用时一律 fail-hard</b>
 *       ——抛 {@code RedisUnavailableException} → 网关 503 + ERROR 日志报警，
 *       不静默降级到本地缓存（降级会让频控超发、负载失衡，属不可接受的静默损失）。</li>
 * </ul>
 *
 * <p>⚠ 摘除状态（outage）<b>不在本词表内</b>：它是纯本地统计，一律走 Caffeine
 * （{@code TargetOutageStore} 只有 Caffeine 一个实现），因此没有多节点档位。
 * 摘除是「本节点自己的异常观察」，不需要跨节点共享；放 Redis 反而让 Redis 故障
 * 直接打掉网关的自我保护能力。</p>
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
