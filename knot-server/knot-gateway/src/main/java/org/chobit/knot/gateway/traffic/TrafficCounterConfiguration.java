package org.chobit.knot.gateway.traffic;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.entity.ProviderAccountEntity;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 计数存储装配：按 {@code knot.traffic.store} 二选一，同一时刻只有一个 {@link TrafficCounterStore}。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(TrafficCounterProperties.class)
public class TrafficCounterConfiguration {

    /**
     * 策略数据源：走 GatewayDataService（库查询 + 10 分钟缓存），与计数存储相互独立。
     */
    @Bean
    public TrafficPolicySource trafficPolicySource(GatewayDataService dataService) {
        return new TrafficPolicySource() {

            @Override
            public TrafficPolicies policiesOf(String resourceType, Long resourceId) {
                return dataService.loadTrafficPolicies(resourceType, resourceId);
            }

            @Override
            public Long providerAccountIdOf(String providerAccountCode) {
                ProviderAccountEntity account = dataService.getProviderAccountByCode(providerAccountCode);
                return account == null ? null : account.getId();
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
     * 单节点实现：进程内 Caffeine，默认开启。
     */
    @Bean
    @ConditionalOnProperty(name = "knot.traffic.store", havingValue = "caffeine", matchIfMissing = true)
    public TrafficCounterStore caffeineTrafficCounterStore(TrafficCounterProperties properties) {
        log.info("Traffic counter store: caffeine (maximumSize={}, zone={})",
                properties.getMaximumSize(), properties.getZone());
        return new CaffeineTrafficCounterStore(properties.getMaximumSize());
    }

    /**
     * 多节点实现：Redis + Lua。切到这一档时各节点共享同一份计数，额度口径即为全局口径。
     */
    @Bean
    @ConditionalOnProperty(name = "knot.traffic.store", havingValue = "redis")
    public TrafficCounterStore redisTrafficCounterStore(StringRedisTemplate stringRedisTemplate,
                                                        TrafficCounterProperties properties) {
        log.info("Traffic counter store: redis (keyPrefix={}, zone={}, failOpen={})",
                properties.getKeyPrefix(), properties.getZone(), properties.isFailOpen());
        return new RedisTrafficCounterStore(stringRedisTemplate, properties.isFailOpen());
    }
}
