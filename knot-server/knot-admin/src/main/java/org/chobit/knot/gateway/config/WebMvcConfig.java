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
                        "/api/auth/force-password-change",
                        "/api/auth/logout",
                        "/api/health"
                );
        registry.addInterceptor(adminAuthorizationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/force-password-change",
                        "/api/auth/logout",
                        "/api/health",
                        // 「加载自身权限」的接口只做认证、不做接口级授权：
                        // 否则用户一旦缺少其绑定权限，就永远拿不到自己的权限列表（自锁死）。
                        "/api/me/authorizations"
                );
    }
}
