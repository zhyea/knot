package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayRuntimeProperties.class)
public class GatewayConfiguration {
}
