package org.chobit.knot.gateway.load;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 在途计量存储装配：按 {@code knot.load.store} 二选一，同一时刻只有一个 {@link ModelLoadStore}。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ModelLoadProperties.class)
public class ModelLoadStoreConfiguration {

    /**
     * 键工厂，与存储实现无关。
     */
    @Bean
    public ModelLoadKeys modelLoadKeys(ModelLoadProperties properties) {
        return new ModelLoadKeys(properties.getKeyPrefix());
    }

    /**
     * 单节点实现：进程内 Caffeine，默认开启。
     *
     * <p>只统计本节点在途，集群部署下是近似口径，启动日志明示以便排查。</p>
     */
    @Bean
    @ConditionalOnProperty(name = "knot.load.store", havingValue = "caffeine", matchIfMissing = true)
    public ModelLoadStore caffeineModelLoadStore(ModelLoadProperties properties) {
        log.info("Model load store: caffeine (maximumSize={}, ttlSeconds={}); "
                        + "cluster deployment should switch to 'redis' for a global view",
                properties.getMaximumSize(), properties.getTtlSeconds());
        return new CaffeineModelLoadStore(properties.getMaximumSize(), properties.getTtlSeconds());
    }

    /**
     * 多节点实现：Redis + Lua。切到这一档时各节点共享同一份在途数，负载口径即为全局口径。
     */
    @Bean
    @ConditionalOnProperty(name = "knot.load.store", havingValue = "redis")
    public ModelLoadStore redisModelLoadStore(StringRedisTemplate stringRedisTemplate,
                                              ModelLoadProperties properties) {
        log.info("Model load store: redis (keyPrefix={}, ttlSeconds={}, failOpen={})",
                properties.getKeyPrefix(), properties.getTtlSeconds(), properties.isFailOpen());
        return new RedisModelLoadStore(stringRedisTemplate, properties.getTtlSeconds(), properties.isFailOpen());
    }
}
