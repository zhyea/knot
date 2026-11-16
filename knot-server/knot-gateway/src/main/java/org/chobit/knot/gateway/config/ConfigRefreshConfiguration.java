package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 配置缓存主动刷新的属性注册（{@code knot.gateway.config-refresh.*}）。
 *
 * <p>{@code @Scheduled} 由 {@code org.chobit.knot.gateway.config.GatewaySchedulingConfiguration} 开启，这里不重复 {@code @EnableScheduling}。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfigRefreshProperties.class)
public class ConfigRefreshConfiguration {
}
