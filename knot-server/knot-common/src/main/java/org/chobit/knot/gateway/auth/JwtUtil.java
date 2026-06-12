package org.chobit.knot.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

public class JwtUtil {
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_ACCESS = "ACCESS";
    private static final String PURPOSE_FORCE_PASSWORD_CHANGE = "FORCE_PASSWORD_CHANGE";

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "knot-ai-gateway-jwt-secret-key-must-be-at-least-256-bits-long!".getBytes(StandardCharsets.UTF_8)
    );
    private static final long EXPIRATION_MS = 24L * 60 * 60 * 1000;
    private static final long PASSWORD_CHANGE_EXPIRATION_MS = 10L * 60 * 1000;

    private JwtUtil() {
    }

    /**
     * Generates a standard access token.
     */
    public static String generateToken(Long userId, String username) {
        return generateToken(userId, username, List.of());
    }

    /**
     * Generates a standard access token.
     */
    public static String generateToken(Long userId, String username, List<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLES, roles != null ? roles : List.of())
                .claim(CLAIM_PURPOSE, PURPOSE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(KEY)
                .compact();
    }

    /**
     * Generates a short-lived token used only for forced password changes.
     */
    public static String generatePasswordChangeToken(Long userId, String username) {
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_PURPOSE, PURPOSE_FORCE_PASSWORD_CHANGE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + PASSWORD_CHANGE_EXPIRATION_MS))
                .signWith(KEY)
                .compact();
    }

    /**
     * Parses a raw JWT token.
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Parses and validates a standard access token.
     */
    public static Claims parseAccessToken(String token) {
        Claims claims = parseToken(token);
        validatePurpose(claims, PURPOSE_ACCESS);
        return claims;
    }

    /**
     * Parses and validates a forced-password-change token.
     */
    public static Claims parsePasswordChangeToken(String token) {
        Claims claims = parseToken(token);
        validatePurpose(claims, PURPOSE_FORCE_PASSWORD_CHANGE);
        return claims;
    }

    /**
     * Returns whether the provided token can be parsed successfully.
     */
    public static boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void validatePurpose(Claims claims, String expectedPurpose) {
        String actualPurpose = claims.get(CLAIM_PURPOSE, String.class);
        if (!expectedPurpose.equals(actualPurpose)) {
            throw new IllegalArgumentException("invalid token purpose");
        }
    }
}
