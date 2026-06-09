package org.chobit.knot.gateway.plugin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PluginSinkProperties.class)
public class PluginAutoConfiguration {
}
