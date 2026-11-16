package org.chobit.knot.gateway.traffic;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.redis.RedisCache;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多节点计数存储：Redis。
 * 与 {@link CaffeineTrafficCounterStore} 实现同一个 {@link TrafficCounterStore} 抽象：累加与设置过期时刻由一段 Lua 原子完成。
 * 所有 Redis 访问经统一门面 {@link RedisCache}，异常分类与 ERROR 日志都在门面内完成，本类只剩「脚本 + 业务语义」。
 * 故障口径 fail-hard：Redis 不可用时抛 {@code RedisUnavailableException} → 网关 503。
 */
@Slf4j
public class RedisTrafficCounterStore implements TrafficCounterStore {

    private static final String DOMAIN = "traffic";

    /** 脏值日志节流：每 N 次一条，脚本持续异常时不把日志打爆 */
    private static final int DIRTY_LOG_EVERY = 100;

    /**
     * 累加并设置绝对过期时刻；返回累加后的值。
     */
    private static final RedisScript<Long> ADD_AND_GET = RedisScript.of("""
            local current = redis.call('INCRBY', KEYS[1], ARGV[1])
            redis.call('PEXPIREAT', KEYS[1], ARGV[2])
            return current
            """, Long.class);

    private final RedisCache cache;

    /** 脏值（脚本返回 null / 非数字）次数：静默兜底会让频控与额度少计，必须可观测 */
    private final AtomicLong dirtyValues = new AtomicLong();

    /**
     * @param cache 统一 Redis 门面（内部统一 fail-hard 与 ERROR 日志）
     */
    public RedisTrafficCounterStore(RedisCache cache) {
        this.cache = cache;
    }

    @Override
    public String id() {
        return "redis";
    }

    @Override
    public long addAndGet(String key, long delta, long expireAtMillis) {
        Long result = cache.eval(DOMAIN, "addAndGet", ADD_AND_GET, List.of(key),
                Long.toString(delta), Long.toString(expireAtMillis));
        // Lua 正常返回不会是 null；为 null 说明脚本行为异常，按增量兜底但必须暴露
        if (result == null) {
            markDirty("addAndGet returned null, fell back to delta", key, null);
            return delta;
        }
        return result;
    }

    @Override
    public long get(String key) {
        String value = cache.value(DOMAIN, "get", key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            // 脏数据不是 Redis 故障：按「尚未产生用量」处理，但必须可观测——否则等于静默放行
            markDirty("non-numeric counter value, treated as 0", key, value);
            return 0L;
        }
    }

    /**
     * 脏值打点：单调计数 + 节流 ERROR 日志；返回语义由调用方决定的兜底值保持不变，只让问题可见。
     */
    private void markDirty(String reason, String key, String value) {
        long total = dirtyValues.incrementAndGet();
        if (total % DIRTY_LOG_EVERY == 1) {
            log.error("Redis traffic counter {} (total={}): key={} value={}", reason, total, key, value);
        }
    }

    /**
     * 脏值累计次数（监控面）：非 0 意味着频控 / 额度读数不可信。
     */
    long dirtyValues() {
        return dirtyValues.get();
    }
}