package org.chobit.knot.gateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.gateway.ApiResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * JWT 认证拦截器：从请求头提取 token 并校验
 */
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTH = "Authorization";
    private static final String BEARER = "Bearer ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String auth = request.getHeader(HEADER_AUTH);
        if (auth == null || !auth.startsWith(BEARER)) {
            writeError(response, "未登录或 token 无效");
            return false;
        }
        String token = auth.substring(BEARER.length()).trim();
        if (!JwtUtil.isValid(token)) {
            writeError(response, "token 已过期或无效");
            return false;
        }
        Claims claims = JwtUtil.parseToken(token);
        request.setAttribute("userId", claims.get("userId", Long.class));
        request.setAttribute("username", claims.getSubject());
        return true;
    }

    private void writeError(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> result = Map.of("success", false, "message", message, "data", null);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
