package org.chobit.knot.gateway.load;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 最空闲选模所需的在途并发计量配置（{@code knot.load.*}）。
 * {@code store} 选存储实现：单节点 {@code caffeine}（默认，节点内近似）、多节点 {@code redis}（全局口径），两种实现共用同一套键。
 * {@code redis} 档 fail-hard，无 fail-open 开关与降级档；Redis 不可用时抛异常，返回网关 503 并打 ERROR 报警。
 */
@Data
@ConfigurationProperties(prefix = "knot.load")
public class ModelLoadProperties {

    /**
     * 计量存储实现：{@code caffeine}（单节点，默认）/ {@code redis}（多节点全局）。
     * 保持 {@code null} 默认值，表示未显式设置，归一时回落 {@code caffeine}。
     */
    private String store;

    /**
     * 存储键前缀：redis 档用于多个环境共用同一 Redis 时隔离；caffeine 档为进程内键结构。
     * 两种档位共用同一套键工厂，切档不改键结构。
     */
    private String keyPrefix = "knot:load";

    /**
     * 在途计数的存活时间（秒）：计数泄漏的上界，也是条目在无新请求时的回收时间。
     * 取值须显著大于单请求的最长耗时（含长流式响应）。
     */
    private long ttlSeconds = 300L;

    /**
     * Caffeine 存储的最大条目数，超过后按 LRU 淘汰。
     */
    private long maximumSize = 20_000L;

}
