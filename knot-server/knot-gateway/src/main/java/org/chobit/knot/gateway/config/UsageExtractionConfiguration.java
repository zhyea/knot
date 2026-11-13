package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用 {@link UsageExtractionProperties} 绑定。 */
@Configuration
@EnableConfigurationProperties(UsageExtractionProperties.class)
public class UsageExtractionConfiguration {
}
