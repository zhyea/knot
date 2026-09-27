package org.chobit.knot.gateway.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.gateway.service.AdminAuthorizationService;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    private final AdminAuthorizationService adminAuthorizationService;
    private final CurrentAuth currentAuth;

    /**
     * Constructs a new instance.
     */
    public AdminAuthorizationInterceptor(AdminAuthorizationService adminAuthorizationService,
                                         CurrentAuth currentAuth) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.currentAuth = currentAuth;
    }

    /**
     * Validates the current admin request authorization before the controller executes.
     * <p>
     * 默认拒绝：所有 {@code /api/**} 的 handler 方法都要经过接口级授权，无绑定即 403。
     * 公共接口通过在 {@code WebMvcConfig} 中显式配置 excludePathPatterns 白名单放行。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String pathPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pathPattern == null || pathPattern.isBlank()) {
            pathPattern = request.getRequestURI();
        }
        adminAuthorizationService.validateApiAccess(currentAuth.currentUserId(), request.getMethod(), pathPattern);
        return true;
    }
}
