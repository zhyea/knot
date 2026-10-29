package org.chobit.knot.gateway.admin;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;

/**
 * 网关 {@code /_admin/**} 的准入判定（<b>单一实现</b>）。
 *
 * <p>⭐ <b>为什么必须只有一份</b>：网关没有全局鉴权拦截器（业务鉴权在请求 handler 里按
 * key/rule 做），所以每个管理面控制器都得自带保护。一旦有两个控制器各写一遍
 * 「配了令牌就比对请求头、没配就只放行 loopback」，下一次有人放宽其中一处（比如加个
 * 「内网网段也放行」），另一处不会跟着改——而漏掉的那一处在生产上就是一个免鉴权的
 * 管理入口。判据很硬：<b>准入是安全边界，不是每个控制器的私有细节</b>。</p>
 *
 * <p>调用方只需要把自己那份配置里的令牌传进来（{@code null}/空白 表示未配置），
 * 至于「未配置时怎么办」——这里统一为「仅放行 loopback」，不给调用方选择权。</p>
 *
 * <p>⚠ <b>loopback 判定用 {@link InetSocketAddress#getAddress()} 而不是字符串比对</b>
 * （2026-10-20 WebFlux 批2）：Netty 下拿不到 {@code 127.0.0.1} / {@code ::1} 这种字面量。
 * 无对端地址（如本地 in-process 调用、未绑定连接的测试请求）按<b>非</b> loopback 处理——
 * 宁可拒绝，也不放宽准入。</p>
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
            if (!expectedToken.equals(actual)) {
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

    private static boolean isLoopback(InetSocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return false;
        }
        return remoteAddress.getAddress() != null && remoteAddress.getAddress().isLoopbackAddress();
    }
}
