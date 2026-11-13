package org.chobit.knot.gateway.admin;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 网关 {@code /_admin/**} 的准入判定（单一实现）。
 * 调用方把配置里的令牌传进来（{@code null}/空白 表示未配置）；未配置时统一仅放行 loopback。
 * loopback 判定用 {@link InetSocketAddress#getAddress()}；无对端地址（本地 in-process 调用）按非 loopback 处理。
 */
public final class GatewayAdminAccessSupport {

    private GatewayAdminAccessSupport() {
    }

    /**
     * 校验本次请求是否有权访问管理面。
     *
     * @param request       当前请求（用于读请求头与对端地址）
     * @param expectedToken 部署配置里的共享令牌；{@code null} 或空白表示未配置
     * @throws ResponseStatusException 403：令牌不符，或未配令牌且来源不是 loopback
     */
    public static void requireAccess(ServerHttpRequest request, String expectedToken) {
        if (expectedToken != null && !expectedToken.isBlank()) {
            String actual = request.getHeaders().getFirst(GatewayHeaders.X_KNOT_ADMIN_TOKEN);
            if (!tokenMatches(expectedToken, actual)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "invalid or missing X-Knot-Admin-Token");
            }
            return;
        }
        if (!isLoopback(request.getRemoteAddress())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "gateway admin api requires X-Knot-Admin-Token or loopback access");
        }
    }

    /**
     * 用 {@link MessageDigest#isEqual} 常量时间比对共享令牌；缺令牌（{@code null}）直接判否。
     */
    private static boolean tokenMatches(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isLoopback(InetSocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return false;
        }
        return remoteAddress.getAddress() != null && remoteAddress.getAddress().isLoopbackAddress();
    }
}
