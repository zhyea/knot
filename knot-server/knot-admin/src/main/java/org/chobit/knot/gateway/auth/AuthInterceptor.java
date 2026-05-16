package org.chobit.knot.gateway.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.gateway.error.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器：从请求头提取 token 并校验；失败时抛出 {@link UnauthorizedException} 交由全局异常处理。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTH = "Authorization";
    private static final String BEARER = "Bearer ";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader(HEADER_AUTH);
        if (auth == null || !auth.startsWith(BEARER)) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        String token = auth.substring(BEARER.length()).trim();
        if (token.isEmpty() || !JwtUtil.isValid(token)) {
            throw new UnauthorizedException("token 已过期或无效");
        }
        try {
            Claims claims = JwtUtil.parseToken(token);
            request.setAttribute("userId", claims.get("userId", Long.class));
            request.setAttribute("username", claims.getSubject());
            return true;
        } catch (Exception e) {
            throw new UnauthorizedException("token 已过期或无效");
        }
    }
}
