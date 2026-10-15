package org.chobit.knot.gateway.load;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.redis.RedisCache;
import org.chobit.knot.gateway.redis.RedisStoreModeResolver;
import org.chobit.knot.gateway.redis.RedisStoreModes;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在途计量存储装配：模式由 {@code knot.redis.load.store} 决定（默认 {@code caffeine}），
 * 两种模式共用同一个 {@link ModelLoadStore} 抽象，同一时刻只注册一个实现。
 *
 * <p>模式值统一由 {@link RedisStoreModeResolver} 归一（含旧键兼容），与频控同一套读法。</p>
 *
 * <p><b>故障口径：Redis 档fail-hard。</b>在途数是 {@code LEAST_LOAD} 选路的唯一数据源，
 * 静默按 0 处理会让所有模型被看成「不拥塞」，负载均衡退化成权重序——已过载的模型继续被加压。
 * 因此 Redis 不可用时直接抛 {@code RedisUnavailableException} → 网关 503 + ERROR 日志报警。</p>
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
     *
     * <p>{@code caffeine} 只统计本节点在途，集群部署下是近似口径；
     * {@code redis} 为全局口径，Redis 故障时 fail-hard。</p>
     */
    @Bean
    public ModelLoadStore modelLoadStore(ModelLoadProperties properties,
                                          RedisCache cache,
                                          RedisStoreModeResolver modes) {
        String mode = modes.modeOf(DOMAIN, properties.getStore());
        switch (mode) {
            case RedisStoreModes.CAFFEINE -> {
                log.info("Model load store: caffeine (maximumSize={}, ttlSeconds={}); "
                                + "cluster deployment should switch to 'redis' for a global view",
                        properties.getMaximumSize(), properties.getTtlSeconds());
                return new CaffeineModelLoadStore(properties.getMaximumSize(), properties.getTtlSeconds());
            }
            case RedisStoreModes.REDIS -> {
                log.info("Model load store: redis (keyPrefix={}, ttlSeconds={}, failHard=true)",
                        properties.getKeyPrefix(), properties.getTtlSeconds());
                return new RedisModelLoadStore(cache, properties.getTtlSeconds());
            }
            default -> throw new IllegalStateException("Unhandled load store mode: " + mode);
        }
    }
}