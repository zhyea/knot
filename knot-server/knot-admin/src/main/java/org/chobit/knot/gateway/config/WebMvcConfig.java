package org.chobit.knot.gateway.config;

import org.chobit.knot.gateway.auth.AdminAuthorizationInterceptor;
import org.chobit.knot.gateway.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminAuthorizationInterceptor adminAuthorizationInterceptor;

    /**
     * Constructs a new instance.
     */
    public WebMvcConfig(AuthInterceptor authInterceptor,
                        AdminAuthorizationInterceptor adminAuthorizationInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminAuthorizationInterceptor = adminAuthorizationInterceptor;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/health"
                );
        registry.addInterceptor(adminAuthorizationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/health"
                );
    }
}
