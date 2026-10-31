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
 * 两种模式（进程内 / Redis）共用同一个 {@link TrafficCounterStore} 抽象，
 * 同一时刻只注册一个实现。
 *
 * <p>模式值统一由 {@link StoreModeResolver} 归一，与在途计量同一套读法，
 * 不再各写一份 {@code @ConditionalOnProperty} 分支。</p>
 *
 * <p><b>故障口径：Redis 档 fail-hard。</b>频控计数承载全局额度口径，静默按「计0」放行
 * 会让多节点下的实际限额悄悄涨到「配置值 × 节点数」，直接超发且无感知。
 * 因此 Redis 不可用时直接抛 {@code RedisUnavailableException} → 网关 503 + ERROR 日志报警。</p>
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
     *
     * <p><b>故障口径：Redis 档 fail-hard。</b>Redis 不可用时抛
     * {@code RedisUnavailableException} → 网关 503，绝不按「计0」静默放行：
     * 那样多节点下的实际限额会悄悄涨到「配置值 × 节点数」，超发无感知。</p>
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
