package org.chobit.knot.gateway.outage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异常摘除的运行时配置（{@code knot.outage.*}）。
 *
 * <p><b>没有 {@code store} 开关</b>：摘除状态一律用进程内 Caffeine。
 * 理由是摘除表达的是「<b>本节点自己观察到的异常</b>」（超时率 / 异常率 / 慢信号），
 * 本就不需要跨节点共享；把它放 Redis 只会让 Redis 故障同时打掉网关的自我保护能力——
 * 摘除失效意味着坏模型继续被加压，比摘除口径不全局严重得多。</p>
 *
 * <p>因此 {@code knot.outage.store} 已废弃：显式配了 {@code redis} 或
 * {@code fallback} 的部署需改回 {@code caffeine}（或直接删掉该项，默认即 Caffeine）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.outage")
public class OutageProperties {

    /**
     * 存储键前缀：多环境共用同一进程内的诊断输出时用于区分来源。
     */
    private String keyPrefix = "knot:outage";

    /**
     * 条目的自然回收宽限（毫秒）：追加在「窗口 / 摘除 / 探活中最晚的到期时刻」之后，
     * 防止边界处刚过期就被读成有效状态。
     */
    private long expireGraceMs = 60_000L;

    /**
     * Caffeine 存储的最大条目数，超过后按 LRU 淘汰。
     */
    private long maximumSize = 20_000L;
}