package org.chobit.knot.gateway.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.gateway.error.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * JWT 璁よ瘉鎷︽埅鍣細浠庤姹傚ご鎻愬彇 token 骞舵牎楠岋紱澶辫触鏃舵姏鍑?{@link UnauthorizedException} 浜ょ敱鍏ㄥ眬寮傚父澶勭悊銆?
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTH = "Authorization";
    private static final String BEARER = "Bearer ";

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader(HEADER_AUTH);
        if (auth == null || !auth.startsWith(BEARER)) {
            throw new UnauthorizedException("鏈櫥褰曟垨 token 鏃犳晥");
        }
        String token = auth.substring(BEARER.length()).trim();
        if (token.isEmpty() || !JwtUtil.isValid(token)) {
            throw new UnauthorizedException("token 宸茶繃鏈熸垨鏃犳晥");
        }
        try {
            Claims claims = JwtUtil.parseToken(token);
            request.setAttribute("userId", claims.get("userId", Long.class));
            request.setAttribute("username", claims.getSubject());
            List<String> roles = AuthRoles.fromClaim(claims.get("roles"));
            request.setAttribute("roles", roles);
            return true;
        } catch (Exception e) {
            throw new UnauthorizedException("token 宸茶繃鏈熸垨鏃犳晥");
        }
    }
}
