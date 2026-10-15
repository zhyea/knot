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
 * 网关侧 Redis 访问的唯一门面：多节点共享状态存储（频控计数、在途负载）的
 * Redis 实现一律经由本类，不再各自持有 {@code StringRedisTemplate}。
 *
 * <p><b>定位：实现细节，非对外 API。</b>业务层只依赖各域的存储接口
 * （{@code TrafficCounterStore} / {@code ModelLoadStore}），由
 * {@code *StoreConfiguration} 按配置装配 Caffeine 或 Redis 实现。
 * 本类只被 Redis 实现内部依赖，不改变「单节点 Caffeine / 多节点 Redis」这条选型骨架。
 *
 * <p><b>故障口径：fail-hard，一律抛异常，绝不静默放行。</b>
 * 这三个存储承载的是<b>跨节点共享的全局口径</b>，在Redis 不可用时静默按放行值处理
 * 会产生看不见的损失：频控计 0 等于限额失效（多节点下悄悄涨到「配置值 × 节点数」，
 * 直接超发）、在途记 0 等于所有模型被看成不拥塞（负载均衡退化成权重序，已过载的
 * 模型继续被加压）。因此本类不提供任何「放行值」入口，异常一律包成
 * {@link RedisUnavailableException} 上抛，由
 * {@code GatewayExceptionHandler} 映射为 503 并打 ERROR 日志——
 * ERROR 级日志是本项目的报警通道，运维据此收到通知。</p>
 *
 * <p>统一掉三件事：</p>
 * <ol>
 *   <li><b>异常分类</b>：底层五花八门的 Spring Redis 异常一律包成
 *       {@link RedisUnavailableException}，上层只认这一种信号。</li>
 *   <li><b>错误日志节流</b>：同一 {@code 域+操作+键} 在一分钟内只打一条 ERROR。
 *       故障期间这是每请求都走的路径，不节流会把日志盘打爆，反而丢故障现场。</li>
 *   <li><b>日志字段固定</b>：{@code domain} / {@code operation} / {@code key}
 *       三个字段写进消息文本，便于日志采集侧按关键字配报警规则。</li>
 * </ol>
 *
 * <p>刻意<b>不</b>做的事：不做键命名（键归各 {@code *Keys}）、不做序列化策略统一
 * （两处状态结构不同）、不做重试（重试会把 Redis 慢放大成正反馈）、
 * <b>不做降级</b>（见上：降级必然产生静默损失，不如直接失败并报警）。</p>
 */
@Slf4j
@Component
public class RedisCache {

    /**
     * 同一故障点的错误日志节流窗口：一分钟一条足够定位，不至于刷爆日志。
     *
     * <p>⚠ 节流只压「日志条数」，不压「异常上抛」——每次 Redis 故障都会照常抛，
     * 对应请求一律 503，不会因为日志节流而变成静默成功。</p>
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

    /**
     * Constructs a new instance.
     */
    public RedisCache(StringRedisTemplate template) {
        this.template = template;
    }

    /**
     * 执行 Lua 脚本：成功返回值（可能为 {@code null}）；Redis 不可用时抛
     * {@link RedisUnavailableException}，并按节流窗口打一条 ERROR 日志。
     *
     * @param domain    存储域（traffic / load），仅用于日志与异常定位
     * @param operation 操作名（常量字符串）
     * @param script    脚本；多节点下的读-改-写必须写成<b>单段</b>脚本
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