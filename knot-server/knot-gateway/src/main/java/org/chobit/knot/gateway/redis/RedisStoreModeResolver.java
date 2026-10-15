package org.chobit.knot.gateway.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 存储模式的<b>唯一归一处</b>：把 {@code knot.redis.<domain>.store}
 * 与旧配置键（{@code knot.<domain>.store}）归一成一份运行时口径。
 *
 * <p>各域的装配类不再各写一套合法性判断，全来这里取归一后的值，保证多节点口径一致。</p>
 *
 * <p><b>优先级</b>：新键 → 旧键（仅当新键未显式设置）→ 内置默认值（caffeine）。
 * 「未显式设置」靠包装类型的 {@code null} 判定——各域 Properties 的 {@code store}
 * 必须保持 {@code null} 默认值，一旦给了默认值就再也分不清「显式配了 caffeine」与「没配」，
 * 旧键兼容会整体失效。</p>
 *
 * <p>⚠ 本类<b>不再</b>归一故障口径：fail-open 与降级机制已下线，
 * Redis 档一律 fail-hard，故障处置固定为「抛异常 + ERROR 日志」。</p>
 */
@Slf4j
@Component
public class RedisStoreModeResolver {

    private final Environment environment;

    /**
     * Constructs a new instance.
     */
    public RedisStoreModeResolver(Environment environment) {
        this.environment = environment;
    }

    /**
     * 归一存储模式。
     *
     * @param domain     存储域（traffic / load）
     * @param configured 该域 Properties 里的模式值，{@code null} 表示未显式设置
     * @return {@link RedisStoreModes} 中的合法模式值
     * @throws IllegalStateException 模式值无法识别（宁可启动失败，也不要静默按单节点跑）
     */
    public String modeOf(String domain, String configured) {
        String normalized = RedisStoreModes.normalize(configured);
        if (normalized == null) {
            String legacy = environment.getProperty("knot." + domain + ".store");
            if (RedisStoreModes.normalize(legacy) != null) {
                log.warn("Deprecated config key in use: knot.{}.store={}; migrate to knot.redis.{}.store",
                        domain, legacy, domain);
                normalized = RedisStoreModes.normalize(legacy);
            }
        }
        if (normalized == null) {
            normalized = RedisStoreModes.CAFFEINE;
        }
        if (!RedisStoreModes.valid(normalized)) {
            throw new IllegalStateException("Unknown store mode for domain " + domain + ": " + normalized
                    + "; expected one of caffeine / redis");
        }
        return normalized;
    }
}