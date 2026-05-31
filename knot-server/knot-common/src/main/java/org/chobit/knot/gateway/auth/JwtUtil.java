package org.chobit.knot.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 浠ょ墝宸ュ叿绫?
 */
public class JwtUtil {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "knot-ai-gateway-jwt-secret-key-must-be-at-least-256-bits-long!".getBytes(StandardCharsets.UTF_8)
    );
    private static final long EXPIRATION_MS = 24L * 60 * 60 * 1000; // 24h

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static String generateToken(Long userId, String username) {
        return generateToken(userId, username, List.of());
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static String generateToken(Long userId, String username, List<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles != null ? roles : List.of())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(KEY)
                .compact();
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public static boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
