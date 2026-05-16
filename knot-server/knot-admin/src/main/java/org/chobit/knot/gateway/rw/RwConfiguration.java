package org.chobit.knot.gateway.rw;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RwProperties.class)
public class RwConfiguration {

    @Bean
    @ConditionalOnProperty(name = "rw.enabled", havingValue = "true", matchIfMissing = true)
    public ApiResponseWrapperAdvice apiResponseWrapperAdvice(RwProperties rwProperties) {
        return new ApiResponseWrapperAdvice(rwProperties);
    }
}
