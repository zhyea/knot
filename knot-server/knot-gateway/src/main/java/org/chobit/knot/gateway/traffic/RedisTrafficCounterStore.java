package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.redis.RedisCache;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 多节点计数存储：Redis。
 *
 * <p>与 {@link CaffeineTrafficCounterStore} 实现同一个 {@link TrafficCounterStore} 抽象：
 * 累加与设置过期时刻由一段 Lua 完成，保证多节点并发下的原子性
 * （拆成 {@code INCRBY} + {@code PEXPIREAT} 两条命令时，进程在两条命令之间崩掉会留下永不过期的键）。</p>
 *
 * <p>所有 Redis 访问经统一门面 {@link RedisCache}，异常分类与 ERROR 日志都在门面内完成，
 * 本类只剩「脚本 + 业务语义」。</p>
 *
 * <p><b>故障口径：fail-hard。</b>Redis 不可用时抛 {@code RedisUnavailableException}
 * → 网关 503。频控承载全局额度口径，静默按「计0」放行会让多节点下的实际限额
 * 悄悄涨到「配置值 × 节点数」，直接超发且无感知——宁可全拒并报警。</p>
 */
public class RedisTrafficCounterStore implements TrafficCounterStore {

    private static final String DOMAIN = "traffic";

    /**
     * 累加并设置绝对过期时刻；返回累加后的值。
     */
    private static final RedisScript<Long> ADD_AND_GET = RedisScript.of("""
            local current = redis.call('INCRBY', KEYS[1], ARGV[1])
            redis.call('PEXPIREAT', KEYS[1], ARGV[2])
            return current
            """, Long.class);

    private final RedisCache cache;

    /**
     * Constructs a new instance.
     *
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
        // Lua 正常返回不会是 null；为 null 说明脚本行为异常，按增量兜底并显式暴露问题
        return result == null ? delta : result;
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
            // 脏数据不是 Redis 故障：按「尚未产生用量」处理，不触发存储故障告警
            return 0L;
        }
    }
}