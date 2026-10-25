package org.chobit.knot.admin.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.admin.error.TooManyRequestsException;
import org.chobit.knot.admin.security.BruteForceGuard;
import org.chobit.knot.common.auth.AuthRoles;
import org.chobit.knot.common.auth.JwtKit;
import org.chobit.knot.common.error.UnauthorizedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTH = "Authorization";
    private static final String BEARER = "Bearer ";

    /**
     * 消费者鉴权防爆破计数器（按客户端 IP 维度），注入自 {@code SecurityConfiguration}。
     */
    private final BruteForceGuard consumerAuthBruteForceGuard;

    /**
     * Constructs a new instance.
     */
    public AuthInterceptor(@Qualifier("consumerAuthBruteForceGuard") BruteForceGuard consumerAuthBruteForceGuard) {
        this.consumerAuthBruteForceGuard = consumerAuthBruteForceGuard;
    }

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
        String clientIp = request.getRemoteAddr();
        // 该 IP 在窗口内失败次数已达封禁阈值：直接快速拒绝，抬高 token 爆破成本。
        if (consumerAuthBruteForceGuard.isBlocked(clientIp)) {
            throw new TooManyRequestsException("请求过于频繁，请稍后再试");
        }
        String auth = request.getHeader(HEADER_AUTH);
        if (auth == null || !auth.startsWith(BEARER)) {
            consumerAuthBruteForceGuard.recordFailure(clientIp);
            throw new UnauthorizedException("未登录或 token 无效");
        }
        String token = auth.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            consumerAuthBruteForceGuard.recordFailure(clientIp);
            throw new UnauthorizedException("未登录或 token 无效");
        }
        try {
            Claims claims = JwtKit.parseAccessToken(token);
            request.setAttribute("userId", claims.get("userId", Long.class));
            request.setAttribute("username", claims.getSubject());
            List<String> roles = AuthRoles.fromClaim(claims.get("roles"));
            request.setAttribute("roles", roles);
            return true;
        } catch (Exception e) {
            consumerAuthBruteForceGuard.recordFailure(clientIp);
            throw new UnauthorizedException("token 已过期或无效");
        }
    }
}
