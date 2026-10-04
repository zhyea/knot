package org.chobit.knot.gateway.traffic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 多节点计数存储：Redis。
 *
 * <p>与 {@link CaffeineTrafficCounterStore} 实现同一个 {@link TrafficCounterStore} 抽象：
 * 累加与设置过期时刻由一段 Lua 完成，保证多节点并发下的原子性
 * （拆成 {@code INCRBY} + {@code PEXPIREAT} 两条命令时，进程在两条命令之间崩掉会留下永不过期的键）。</p>
 *
 * <p>启用方式：{@code knot.traffic.store=redis}（并配好 {@code spring.data.redis.*}）。
 * 存储层不感知业务语义，因此网关侧代码无需任何改动。</p>
 */
@Slf4j
public class RedisTrafficCounterStore implements TrafficCounterStore {

    /**
     * 累加并设置绝对过期时刻；返回累加后的值。
     */
    private static final RedisScript<Long> ADD_AND_GET = RedisScript.of("""
            local current = redis.call('INCRBY', KEYS[1], ARGV[1])
            redis.call('PEXPIREAT', KEYS[1], ARGV[2])
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final boolean failOpen;

    /**
     * Constructs a new instance.
     *
     * @param redis    字符串模板（键与值都是计数，不需要序列化器）
     * @param failOpen Redis 不可用时是否放行：{@code true} 记日志后按「未超限」处理
     */
    public RedisTrafficCounterStore(StringRedisTemplate redis, boolean failOpen) {
        this.redis = redis;
        this.failOpen = failOpen;
    }

    @Override
    public String id() {
        return "redis";
    }

    @Override
    public long addAndGet(String key, long delta, long expireAtMillis) {
        try {
            Long result = redis.execute(ADD_AND_GET, List.of(key),
                    Long.toString(delta), Long.toString(expireAtMillis));
            return result == null ? delta : result;
        } catch (RuntimeException ex) {
            return onFailure(key, ex);
        }
    }

    @Override
    public long get(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException ex) {
            return onFailure(key, ex);
        }
    }

    /**
     * Redis 故障时的处置：默认 fail-open（返回 {@code 0}，即「尚未产生用量」），
     * 避免缓存层故障直接打挂网关流量。
     */
    private long onFailure(String key, RuntimeException ex) {
        if (failOpen) {
            log.warn("Traffic counter store unavailable, fail-open for key={}: {}", key, ex.getMessage());
            return 0L;
        }
        throw ex;
    }
}
