package org.chobit.knot.gateway.config;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;
import java.util.List;

/**
 * Allows the admin console to call the gateway directly for streaming debug requests.
 *
 * <p>The origin list is explicit and configurable; wildcard origins are intentionally not
 * enabled because the gateway receives consumer API keys in the Authorization header.</p>
 *
 * <p>2026-10-20（WebFlux 批2）：{@code WebMvcConfigurer#addCorsMappings} 属于 Servlet 栈，
 * 容器换成 Netty 后不再生效——改为注册 {@link CorsWebFilter}。匹配规则仍是 {@code /**}。</p>
 */
@Configuration(proxyBeanMethods = false)
public class GatewayCorsConfiguration {

    private final List<String> allowedOrigins;

    public GatewayCorsConfiguration(
            @Value("${knot.gateway.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Rule", "traceparent",
                "X-Knot-Test-Return-Usage"));
        config.setExposedHeaders(List.of("Content-Type", "Cache-Control", "X-Accel-Buffering",
                GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME, GatewayHeaders.X_KNOT_MODEL,
                // W3C 链路追踪：网关在响应头回写 traceparent，浏览器跨域场景必须显式暴露，
                // 否则前端拿不到该头（与日志里的 traceId/spanId 对不上）
                GatewayHeaders.TRACEPARENT));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
