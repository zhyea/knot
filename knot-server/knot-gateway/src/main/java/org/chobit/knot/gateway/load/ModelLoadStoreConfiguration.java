package org.chobit.knot.gateway.load;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.redis.RedisCache;
import org.chobit.knot.gateway.store.StoreMode;
import org.chobit.knot.gateway.store.StoreModeResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在途计量存储装配：模式由 {@code knot.load.store} 决定（默认 {@code caffeine}），两种模式共用 {@link ModelLoadStore} 抽象，同一时刻只注册一个实现。
 * 模式值由 {@link StoreModeResolver} 归一；{@code redis} 档 fail-hard，Redis 不可用时抛 {@code RedisUnavailableException} → 网关 503 + ERROR 报警。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ModelLoadProperties.class)
public class ModelLoadStoreConfiguration {

    private static final String DOMAIN = "load";

    /**
     * 键工厂，与存储实现无关。
     */
    @Bean
    public ModelLoadKeys modelLoadKeys(ModelLoadProperties properties) {
        return new ModelLoadKeys(properties.getKeyPrefix());
    }

    /**
     * 在途计量存储实现。
     * {@code caffeine} 只统计本节点在途，集群部署下是近似口径；
     * {@code redis} 为全局口径，Redis 故障时 fail-hard。
     */
    @Bean
    public ModelLoadStore modelLoadStore(ModelLoadProperties properties,
                                         RedisCache cache,
                                         StoreModeResolver modes) {
        StoreMode mode = modes.modeOf(DOMAIN, properties.getStore());
        switch (mode) {
            case CAFFEINE -> {
                log.info("Model load store: caffeine (maximumSize={}, ttlSeconds={}); "
                        + "cluster deployment should switch to 'redis' for a global view",
                    properties.getMaximumSize(), properties.getTtlSeconds());
                return new CaffeineModelLoadStore(properties.getMaximumSize(), properties.getTtlSeconds());
            }
            case REDIS -> {
                log.info("Model load store: redis (keyPrefix={}, ttlSeconds={}, failHard=true)",
                    properties.getKeyPrefix(), properties.getTtlSeconds());
                return new RedisModelLoadStore(cache, properties.getTtlSeconds());
            }
            default -> throw new IllegalStateException("Unhandled load store mode: " + mode);
        }
    }
}
