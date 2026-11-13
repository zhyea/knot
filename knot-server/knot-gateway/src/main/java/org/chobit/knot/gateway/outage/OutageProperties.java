package org.chobit.knot.gateway.outage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异常摘除的运行时配置（{@code knot.outage.*}）。
 * 无 {@code store} 开关，摘除状态一律用进程内 Caffeine。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.outage")
public class OutageProperties {

    /**
     * 存储键前缀：多环境共用同一进程内的诊断输出时用于区分来源。
     */
    private String keyPrefix = "knot:outage";

    /** 条目的自然回收宽限（毫秒）：追加在「窗口 / 摘除 / 探活中最晚的到期时刻」之后。 */
    private long expireGraceMs = 60_000L;

    /**
     * Caffeine 存储的最大条目数，超过后按 LRU 淘汰。
     */
    private long maximumSize = 20_000L;
}