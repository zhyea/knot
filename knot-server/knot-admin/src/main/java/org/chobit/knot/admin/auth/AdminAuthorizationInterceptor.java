package org.chobit.knot.admin.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.svc.auth.CurrentAuth;
import org.chobit.knot.svc.service.AuthorizationAccessService;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class AdminAuthorizationInterceptor implements HandlerInterceptor {

    private final AuthorizationAccessService authorizationAccessService;
    private final CurrentAuth currentAuth;

    /**
     * Constructs a new instance.
     */
    public AdminAuthorizationInterceptor(AuthorizationAccessService authorizationAccessService,
                                         CurrentAuth currentAuth) {
        this.authorizationAccessService = authorizationAccessService;
        this.currentAuth = currentAuth;
    }

    /**
     * Validates the current admin request authorization before the controller executes.
     * <p>
     * 默认拒绝：所有 {@code /api/**} 的 handler 方法都要经过接口级授权，无绑定即 403。
     * 公共接口通过在 {@code WebMvcConfig} 中显式配置 excludePathPatterns 白名单放行。
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // CORS 预检请求不携带用户 token，也不应参与接口级权限判断。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String pathPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pathPattern == null || pathPattern.isBlank()) {
            pathPattern = request.getRequestURI();
        }
        authorizationAccessService.validateApiAccess(currentAuth.currentUserId(), request.getMethod(), pathPattern);
        return true;
    }
}
