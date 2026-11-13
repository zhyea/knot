package org.chobit.knot.gateway.redis;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 网关侧 Redis 访问的唯一门面：多节点共享状态存储（频控计数、在途负载）的 Redis 实现一律经由本类。
 * 故障一律包成 {@link RedisUnavailableException} 上抛，由 {@code GatewayExceptionHandler} 映射为 503 并打 ERROR 日志，不提供任何放行值。
 * 同一 {@code domain} / {@code operation} / {@code key} 的 ERROR 日志一分钟内只打一条；不做键命名、序列化统一、重试与降级。
 */
@Slf4j
@Component
public class RedisCache {

    /**
     * 同一故障点的错误日志节流窗口：一分钟一条。节流只压日志条数，异常照常上抛，对应请求一律 503。
     */
    private static final Duration ERROR_LOG_WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate template;

    /**
     * 故障日志节流：命中即本窗口内不再重复打 ERROR（仅故障路径访问，正常路径零开销）。
     */
    private final Cache<String, Boolean> logThrottle = Caffeine.newBuilder()
        .expireAfterWrite(ERROR_LOG_WINDOW)
        .maximumSize(10_000L)
        .build();

    public RedisCache(StringRedisTemplate template) {
        this.template = template;
    }

    /**
     * 执行 Lua 脚本：成功返回值（可能为 {@code null}）；Redis 不可用时抛
     * {@link RedisUnavailableException}，并按节流窗口打一条 ERROR 日志。
     * @param domain    存储域（traffic / load），仅用于日志与异常定位
     * @param operation 操作名（常量字符串）
     * @param script    脚本；多节点下的读-改-写必须写成单段脚本
     * @param keys      键列表
     * @param args      参数
     */
    public <T> T eval(String domain, String operation, RedisScript<T> script, List<String> keys, Object... args) {
        try {
            return template.execute(script, keys, args);
        } catch (RuntimeException ex) {
            throw failure(domain, operation, keys, ex);
        }
    }

    /**
     * 读字符串值；Redis 不可用时抛 {@link RedisUnavailableException}。
     */
    public String value(String domain, String operation, String key) {
        try {
            return template.opsForValue().get(key);
        } catch (RuntimeException ex) {
            throw failure(domain, operation, key, ex);
        }
    }

    /**
     * 批量读字符串值（Redis {@code MGET}）；Redis 不可用时抛 {@link RedisUnavailableException}。
     * 返回列表与入参 {@code keys} 顺序一一对应，key 不存在对应位置为 {@code null}；Redis 可能整体返回 {@code null}，调用方须按缺失处理。
     *
     * @param keys 键列表（非空时至少为 1 个）
     * @return 与 {@code keys} 同序的值列表；key 不存在对应位置为 {@code null}
     */
    public List<String> multiValue(String domain, String operation, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        try {
            return template.opsForValue().multiGet(keys);
        } catch (RuntimeException ex) {
            throw failure(domain, operation, keys, ex);
        }
    }

    /**
     * 统一异常包装：键列表取首个键用于定位。
     */
    private RedisUnavailableException failure(String domain, String operation, List<String> keys, RuntimeException ex) {
        return failure(domain, operation, keys == null || keys.isEmpty() ? null : keys.get(0), ex);
    }

    /**
     * 打节流 ERROR 日志后返回统一异常——ERROR 是本项目的报警通道，
     * 日志采集侧按 {@code domain} / {@code operation} / {@code key} 关键字配规则。
     */
    private RedisUnavailableException failure(String domain, String operation, String key, RuntimeException ex) {
        logErrorOnce(domain, operation, key, ex);
        return new RedisUnavailableException(domain, operation, key, ex);
    }

    /**
     * 节流错误日志：同一 {@code 域+操作+键} 在窗口内只打一条 ERROR。
     * 主要作用是通过日志发出报警
     */
    private void logErrorOnce(String domain, String operation, String key, RuntimeException ex) {
        String slot = domain + '|' + operation + '|' + (key == null ? "-" : key);
        if (logThrottle.getIfPresent(slot) != null) {
            return;
        }
        logThrottle.put(slot, Boolean.TRUE);
        log.error("Redis unavailable, request will fail: domain={}, operation={}, key={}, cause={}",
            domain, operation, key, ex.getMessage(), ex);
    }
}
