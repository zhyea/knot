package org.chobit.knot.gateway.outage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 摘除状态存储装配：按 {@code knot.outage.store} 三选一，同一时刻只有一个 {@link TargetOutageStore}。
 *
 * <ul>
 *   <li>{@code caffeine}（默认）：单节点，进程内视图；</li>
 *   <li>{@code redis}：多节点，全局视图，Redis 故障时 fail-open（不摘除）；</li>
 *   <li>{@code fallback}（多节点推荐）：Redis 优先 + Caffeine 自动降级，恢复后回灌切回。</li>
 * </ul>
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
     * 单节点实现：进程内 Caffeine，默认开启。
     */
    @Bean
    @ConditionalOnProperty(name = "knot.outage.store", havingValue = "caffeine", matchIfMissing = true)
    public TargetOutageStore caffeineTargetOutageStore(OutageProperties properties) {
        log.info("Target outage store: caffeine (maximumSize={}); "
                        + "cluster deployment should switch to 'redis' or 'fallback' for a global view",
                properties.getMaximumSize());
        return new CaffeineTargetOutageStore(properties.getMaximumSize(), properties.getExpireGraceMs());
    }

    /**
     * 多节点实现：Redis Hash + Lua，各节点共享同一份摘除状态。
     */
    @Bean
    @ConditionalOnProperty(name = "knot.outage.store", havingValue = "redis")
    public TargetOutageStore redisTargetOutageStore(StringRedisTemplate stringRedisTemplate,
                                                    OutageProperties properties) {
        log.info("Target outage store: redis (keyPrefix={}, failOpen={})",
                properties.getKeyPrefix(), properties.isFailOpen());
        return new RedisTargetOutageStore(stringRedisTemplate, properties.getExpireGraceMs(), properties.isFailOpen());
    }

    /**
     * 多节点推荐档：Redis 优先，不可用时降级 Caffeine。
     *
     * <p>与纯 {@code redis} 档的差别就在 Redis 抖动时的行为：fail-open 会让摘除能力整体消失
     * （坏目标重新吃流量），降级则保住「本地近似」的摘除。恢复时把本地仍摘除中的条目回灌 Redis。</p>
     */
    @Bean
    @ConditionalOnProperty(name = "knot.outage.store", havingValue = "fallback")
    public TargetOutageStore fallbackTargetOutageStore(StringRedisTemplate stringRedisTemplate,
                                                       OutageProperties properties,
                                                       OutageKeys outageKeys) {
        TargetOutageStore redis = new RedisTargetOutageStore(stringRedisTemplate, properties.getExpireGraceMs(),
                properties.isFailOpen());
        TargetOutageStore caffeine = new CaffeineTargetOutageStore(properties.getMaximumSize(),
                properties.getExpireGraceMs());
        log.info("Target outage store: fallback (redis -> caffeine, recoverProbeIntervalMs={}, keyPrefix={})",
                properties.getRecoverProbeIntervalMs(), properties.getKeyPrefix());
        return new FallbackTargetOutageStore(redis, caffeine, outageKeys, properties.getRecoverProbeIntervalMs(),
                () -> stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping()));
    }
}
