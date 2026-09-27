package org.chobit.knot.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_ACCESS = "ACCESS";
    private static final String PURPOSE_FORCE_PASSWORD_CHANGE = "FORCE_PASSWORD_CHANGE";

    /** 密钥注入入口：环境变量 KNOT_JWT_SECRET 优先，其次系统属性 knot.jwt.secret。 */
    private static final String SECRET_ENV = "KNOT_JWT_SECRET";
    private static final String SECRET_PROPERTY = "knot.jwt.secret";
    /** 仅用于本地开发的兜底值；生产必须通过上面的入口注入。 */
    private static final String DEV_DEFAULT_SECRET =
            "knot-ai-gateway-jwt-secret-key-must-be-at-least-256-bits-long!";

    private static final SecretKey KEY = buildKey();

    private static SecretKey buildKey() {
        String secret = firstNonBlank(System.getenv(SECRET_ENV), System.getProperty(SECRET_PROPERTY));
        boolean devDefault = secret == null;
        if (devDefault) {
            secret = DEV_DEFAULT_SECRET;
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT 签名密钥长度不足 256 bit，请通过环境变量 " + SECRET_ENV + " 注入足够长度的密钥");
        }
        if (devDefault) {
            log.warn("[安全] JWT 签名密钥使用开发默认值，生产环境必须通过环境变量 {} 或系统属性 {} 注入",
                    SECRET_ENV, SECRET_PROPERTY);
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
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
