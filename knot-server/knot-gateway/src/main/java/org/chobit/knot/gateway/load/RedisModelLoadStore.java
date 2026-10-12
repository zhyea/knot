package org.chobit.knot.gateway.load;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 多节点在途计量：Redis。
 *
 * <p>与 {@link CaffeineModelLoadStore} 实现同一个 {@link ModelLoadStore} 抽象。
 * 各脚本都是单段 Lua：多节点并发下的「读-改-写」必须是原子的，
 * 且 {@code INCRBY} 与 {@code PEXPIRE} 必须在同一脚本里——拆成两条命令时进程在中间崩掉会留下
 * 永不过期的键。</p>
 *
 * <p>启用方式：{@code knot.load.store=redis}（并配好 {@code spring.data.redis.*}）。</p>
 */
@Slf4j
public class RedisModelLoadStore implements ModelLoadStore {

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

    private final StringRedisTemplate redis;
    private final long ttlMillis;
    private final boolean failOpen;

    /**
     * Constructs a new instance.
     *
     * @param redis      字符串模板（键与值都是计数，不需要序列化器）
     * @param ttlSeconds 在途计数存活时间（秒）
     * @param failOpen   Redis 不可用时是否放行：{@code true} 记日志后按「在途为 0」处理
     */
    public RedisModelLoadStore(StringRedisTemplate redis, long ttlSeconds, boolean failOpen) {
        this.redis = redis;
        this.ttlMillis = TimeUnit.SECONDS.toMillis(ttlSeconds > 0L ? ttlSeconds : 300L);
        this.failOpen = failOpen;
    }

    @Override
    public String id() {
        return "redis";
    }

    @Override
    public long enter(String key) {
        return execute(ENTER, key, Long.toString(ttlMillis));
    }

    @Override
    public long leave(String key) {
        return execute(LEAVE, key);
    }

    @Override
    public long inflight(String key) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                return 0L;
            }
            long parsed = Long.parseLong(value);
            return parsed < 0L ? 0L : parsed;
        } catch (RuntimeException ex) {
            return onFailure(key, ex);
        }
    }

    private long execute(RedisScript<Long> script, String key, String... args) {
        try {
            Long result = redis.execute(script, List.of(key), args);
            return result == null ? 0L : result;
        } catch (RuntimeException ex) {
            return onFailure(key, ex);
        }
    }

    /**
     * Redis 故障时的处置：默认 fail-open（返回 {@code 0}，即「不拥塞」），
     * 避免缓存层故障直接打挂网关选路。
     */
    private long onFailure(String key, RuntimeException ex) {
        if (failOpen) {
            log.warn("Model load store unavailable, fail-open for key={}: {}", key, ex.getMessage());
            return 0L;
        }
        throw ex;
    }
}
