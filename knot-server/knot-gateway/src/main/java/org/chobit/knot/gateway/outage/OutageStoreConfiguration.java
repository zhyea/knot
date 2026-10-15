package org.chobit.knot.gateway.outage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 摘除状态存储装配：<b>只有进程内 Caffeine 一个实现</b>，无store 开关。
 *
 * <p>摘除统计表达的是「本节点自己观察到的异常」，不需要跨节点共享；
 * 放 Redis 反而让 Redis 故障同时打掉网关的自我保护能力（摘除失效 = 坏模型继续被加压）。
 * 因此 {@code RedisTargetOutageStore} 及其 Redis 键前缀、Lua 脚本已下线。</p>
 *
 * <p>代价是多节点下各节点各摘各的：某个节点摘掉的模型，其他节点可能仍在用。
 * 这是有意的取舍——「摘除口径不全局」比「Redis 故障导致全链路无保护」安全得多。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(OutageProperties.class)
public class OutageStoreConfiguration {

    /**
     * 键工厂，与存储实现无关。
     */
    @Bean
    public OutageKeys outageKeys(OutageProperties properties) {
        return new OutageKeys(properties.getKeyPrefix());
    }

    /**
     * 摘除状态存储：固定进程内 Caffeine。
     */
    @Bean
    public TargetOutageStore targetOutageStore(OutageProperties properties) {
        log.info("Target outage store: caffeine (maximumSize={}, expireGraceMs={}); "
                        + "ejection state is node-local by design (no redis / fallback mode)",
                properties.getMaximumSize(), properties.getExpireGraceMs());
        return new CaffeineTargetOutageStore(properties.getMaximumSize(), properties.getExpireGraceMs());
    }
}