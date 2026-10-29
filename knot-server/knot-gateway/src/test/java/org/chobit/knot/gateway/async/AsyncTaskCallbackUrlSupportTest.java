package org.chobit.knot.gateway.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AsyncTaskCallbackUrlSupport} 的契约：只放行可安全出站的地址。
 *
 * <p>⭐ 这些断言守的是一条<b>安全边界</b>，不是格式校验：{@code callback_url} 是客户端指定、
 * 由网关发起的出站目标，放行一个内网地址就等于开了一条 SSRF 通道。</p>
 */
class AsyncTaskCallbackUrlSupportTest {

    @Test
    @DisplayName("合法公网 http/https 原样返回（trim 后）")
    void acceptsPublicHttp() {
        assertEquals("https://client.example/cb",
                AsyncTaskCallbackUrlSupport.normalize("  https://client.example/cb  ", List.of()));
        assertEquals("http://client.example/cb",
                AsyncTaskCallbackUrlSupport.normalize("http://client.example/cb", List.of()));
        assertEquals("https://8.8.8.8/cb",
                AsyncTaskCallbackUrlSupport.normalize("https://8.8.8.8/cb", List.of()));
    }

    @Test
    @DisplayName("非 http/https / 无 scheme 一律拒绝")
    void rejectsNonHttpScheme() {
        assertNull(AsyncTaskCallbackUrlSupport.normalize("ftp://client.example/cb", List.of()));
        assertNull(AsyncTaskCallbackUrlSupport.normalize("file:///etc/passwd", List.of()));
        assertNull(AsyncTaskCallbackUrlSupport.normalize("client.example/cb", List.of()));
        assertNull(AsyncTaskCallbackUrlSupport.normalize("", List.of()));
        assertNull(AsyncTaskCallbackUrlSupport.normalize(null, List.of()));
    }

    @Test
    @DisplayName("⭐ 回环 / 私网 / 链路本地 / CGNAT 一律拦截（SSRF）")
    void rejectsInternalAddresses() {
        for (String host : List.of("localhost", "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.1",
                "192.168.1.1", "169.254.169.254", "0.0.0.0", "100.64.0.1", "app.localhost")) {
            assertNull(AsyncTaskCallbackUrlSupport.normalize("http://" + host + "/cb", List.of()),
                    "必须拦截内网地址: " + host);
        }
    }

    @Test
    @DisplayName("IPv6 回环 / 链路本地 / 唯一本地拦截，带方括号也不放过")
    void rejectsInternalIpv6() {
        assertNull(AsyncTaskCallbackUrlSupport.normalize("http://[::1]/cb", List.of()));
        assertNull(AsyncTaskCallbackUrlSupport.normalize("http://[fe80::1]/cb", List.of()));
        assertNull(AsyncTaskCallbackUrlSupport.normalize("http://[fd00::1]/cb", List.of()));
    }

    @Test
    @DisplayName("白名单：命中（含后缀）放行，未命中拒绝；空白名单不限制主机")
    void allowList() {
        List<String> allowed = List.of("partner.example");
        assertEquals("https://api.partner.example/cb",
                AsyncTaskCallbackUrlSupport.normalize("https://api.partner.example/cb", allowed));
        assertNull(AsyncTaskCallbackUrlSupport.normalize("https://evil.example/cb", allowed));
        // 后缀匹配不能把 "notpartner.example" 也放进来
        assertNull(AsyncTaskCallbackUrlSupport.normalize("https://notpartner.example/cb", allowed));
    }
}
