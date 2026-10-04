package org.chobit.knot.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Allows the admin console to call the gateway directly for streaming debug requests.
 *
 * <p>The origin list is explicit and configurable; wildcard origins are intentionally not
 * enabled because the gateway receives consumer API keys in the Authorization header.</p>
 */
@Configuration
public class GatewayCorsConfiguration implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public GatewayCorsConfiguration(
            @Value("${knot.gateway.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Rule", "traceparent")
                .exposedHeaders("Content-Type", "Cache-Control", "X-Accel-Buffering")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
