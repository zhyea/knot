package org.chobit.knot.gateway.traffic;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.svc.vo.gateway.ProviderAccountConfigVo;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.gateway.redis.RedisCache;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.gateway.store.StoreMode;
import org.chobit.knot.gateway.store.StoreModeResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 计数存储装配：模式由 {@code knot.traffic.store} 决定（默认 {@code caffeine}），
 * 两种模式（进程内 / Redis）共用同一个 {@link TrafficCounterStore} 抽象，同一时刻只注册一个实现。
 * 模式值统一由 {@link StoreModeResolver} 归一。故障口径：Redis 档 fail-hard，Redis 不可用时抛 {@code RedisUnavailableException} → 网关 503 + ERROR 日志报警。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(TrafficCounterProperties.class)
public class TrafficCounterConfiguration {

    private static final String DOMAIN = "traffic";

    /**
     * 策略数据源：走 GatewayConfigCacheService（库查询 + 10 分钟缓存），与计数存储相互独立。
     */
    @Bean
    public TrafficPolicySource trafficPolicySource(GatewayConfigCacheService dataService) {
        return new TrafficPolicySource() {

            @Override
            public TrafficPolicies policiesOf(String resourceType, Long resourceId) {
                return dataService.loadTrafficPolicies(resourceType, resourceId);
            }

            @Override
            public Long providerAccountIdOf(String providerAccountCode) {
                ProviderAccountConfigVo account = dataService.getProviderAccountByCode(providerAccountCode);
                return account == null ? null : account.id();
            }
        };
    }

    /**
     * 键工厂，与存储实现无关。
     */
    @Bean
    public TrafficCounterKeys trafficCounterKeys(TrafficCounterProperties properties) {
        return new TrafficCounterKeys(properties.getKeyPrefix(), properties.zoneId());
    }

    /**
     * 计数存储实现。
     * 故障口径：Redis 档 fail-hard，Redis 不可用时抛 {@code RedisUnavailableException} → 网关 503，不按「计 0」静默放行。
     */
    @Bean
    public TrafficCounterStore trafficCounterStore(TrafficCounterProperties properties,
                                                   RedisCache cache,
                                                   StoreModeResolver modes) {
        StoreMode mode = modes.modeOf(DOMAIN, properties.getStore());
        switch (mode) {
            case CAFFEINE -> {
                log.info("Traffic counter store: caffeine (maximumSize={}, zone={})",
                        properties.getMaximumSize(), properties.getZone());
                return new CaffeineTrafficCounterStore(properties.getMaximumSize());
            }
            case REDIS -> {
                log.info("Traffic counter store: redis (keyPrefix={}, zone={}, failHard=true)",
                        properties.getKeyPrefix(), properties.getZone());
                return new RedisTrafficCounterStore(cache);
            }
            default -> throw new IllegalStateException("Unhandled traffic store mode: " + mode);
        }
    }
}
