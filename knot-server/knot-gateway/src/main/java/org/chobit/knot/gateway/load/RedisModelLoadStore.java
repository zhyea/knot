package org.chobit.knot.gateway.load;

import org.chobit.knot.gateway.redis.RedisCache;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 多节点在途计量：Redis，与 {@link CaffeineModelLoadStore} 实现同一 {@link ModelLoadStore} 抽象。
 * 脚本均为单段 Lua，保证「读-改-写」原子，{@code INCRBY} 与 {@code PEXPIRE} 在同一脚本内；所有 Redis 访问经 {@link RedisCache}。
 * 故障口径 fail-hard：Redis 不可用抛 {@code RedisUnavailableException} → 网关 503。
 */
public class RedisModelLoadStore implements ModelLoadStore {

    private static final String DOMAIN = "load";

    /**
     * 在途 +1 并刷新存活时间。
     */
    private static final RedisScript<Long> ENTER = RedisScript.of("""
            local current = redis.call('INCRBY', KEYS[1], 1)
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            return current
            """, Long.class);

    /**
     * 在途 −1；键不存在（已过期）时不动键空间，减到负则夹回 0。
     */
    private static final RedisScript<Long> LEAVE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            local current = redis.call('INCRBY', KEYS[1], -1)
            if current < 0 then
                redis.call('SET', KEYS[1], 0)
                return 0
            end
            return current
            """, Long.class);

    private final RedisCache cache;
    private final long ttlMillis;

    /**
     * @param cache      统一 Redis 门面（内部统一 fail-hard 与 ERROR 日志）
     * @param ttlSeconds 在途计数存活时间（秒）
     */
    public RedisModelLoadStore(RedisCache cache, long ttlSeconds) {
        this.cache = cache;
        this.ttlMillis = TimeUnit.SECONDS.toMillis(ttlSeconds > 0L ? ttlSeconds : 300L);
    }

    @Override
    public String id() {
        return "redis";
    }

    @Override
    public long enter(String key) {
        Long result = cache.eval(DOMAIN, "enter", ENTER, List.of(key), Long.toString(ttlMillis));
        // Lua 正常返回不会是 null；为 null 说明脚本行为异常，按 0 兜底并显式暴露问题
        return result == null ? 0L : result;
    }

    @Override
    public long leave(String key) {
        Long result = cache.eval(DOMAIN, "leave", LEAVE, List.of(key));
        return result == null ? 0L : result;
    }

    @Override
    public long inflight(String key) {
        return parseInflight(cache.value(DOMAIN, "inflight", key));
    }

    @Override
    public Map<String, Long> inflightBatch(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<String> values = cache.multiValue(DOMAIN, "inflightBatch", keys);
        Map<String, Long> result = new HashMap<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            String raw = values == null ? null : values.get(i);
            result.put(keys.get(i), parseInflight(raw));
        }
        return result;
    }

    /**
     * 解析在途值：{@code null} / 空 / 非数字 / 负数统一按 {@code 0}（不拥塞）处理。
     * 脏数据不是 Redis 故障，不触发存储故障告警（与单键 {@link #inflight} 语义一致）。
     */
    private static long parseInflight(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed < 0L ? 0L : parsed;
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}