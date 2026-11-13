package org.chobit.knot.gateway.outage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 摘除状态存储装配：只有进程内 Caffeine 一个实现，无 {@code store} 开关。
 * 多节点下各节点各摘各的，某节点摘掉的模型在其他节点可能仍在使用。
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