package org.chobit.knot.gateway.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.gateway.error.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTH = "Authorization";
    private static final String BEARER = "Bearer ";

    /**
     * Validates the access token carried in the request header.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检请求不携带 Authorization，不能按业务请求做 JWT 校验。
        // 若前后端分离部署，拦截 OPTIONS 会把正常预检误报成 401。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader(HEADER_AUTH);
        if (auth == null || !auth.startsWith(BEARER)) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        String token = auth.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        try {
            Claims claims = JwtUtil.parseAccessToken(token);
            request.setAttribute("userId", claims.get("userId", Long.class));
            request.setAttribute("username", claims.getSubject());
            List<String> roles = AuthRoles.fromClaim(claims.get("roles"));
            request.setAttribute("roles", roles);
            return true;
        } catch (Exception e) {
            throw new UnauthorizedException("token 已过期或无效");
        }
    }
}
