package org.chobit.knot.gateway.traffic;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * 频控 / 额度计数配置（{@code knot.traffic.*}）。
 * {@code store} 决定用哪种存储实现：单节点 {@code caffeine}（默认），多节点 {@code redis}（全局口径）；两种实现共用同一套键与判定逻辑，切换只改这一项配置。
 * 故障口径：redis 档一律 fail-hard，无 fail-open 开关、无降级档；Redis 不可用时抛异常 → 网关 503 + ERROR 报警。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.traffic")
public class TrafficCounterProperties {

    /**
     * 计数存储实现：{@code caffeine}（单节点，默认）/ {@code redis}（多节点全局）。
     * 默认值保持 {@code null}（表示「未显式设置」），归一时回落 {@code caffeine}。
     */
    private String store;

    /**
     * 存储键前缀：redis 档用于多个环境共用同一 Redis 时隔离；caffeine 档为进程内键结构。
     * 两种档位共用同一套键工厂，切档不改键结构。
     */
    private String keyPrefix = "knot:traffic";

    /**
     * 日 / 月额度窗口换算所用时区；多节点必须配置成同一个值。
     */
    private String zone = "Asia/Shanghai";

    /**
     * Caffeine 存储的最大条目数，超过后按 LRU 淘汰。
     */
    private long maximumSize = 200_000L;

    /**
     * 时区配置对应的 {@link ZoneId}。
     */
    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
