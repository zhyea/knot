package org.chobit.knot.gateway.outage.admin;

import org.chobit.knot.gateway.model.EjectionPolicy;
import org.chobit.knot.gateway.outage.AnomalyKind;
import org.chobit.knot.gateway.outage.CaffeineTargetOutageStore;
import org.chobit.knot.gateway.outage.OutageKeys;
import org.chobit.knot.gateway.outage.OutageState;
import org.chobit.knot.gateway.outage.TargetOutageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关侧摘除管理面端点的契约：列表 / 手动摘除 / 手动恢复 / 令牌与 loopback 准入。
 *
 * <p>用真实 {@link CaffeineTargetOutageStore}：管理面看到的内容必须与存储真实状态一致，
 * 假存储会把「list 只回非健康条目」这类口径掩盖掉。</p>
 *
 * <p>2026-10-20（WebFlux 批2）：{@code MockMvc} + {@code setRemoteAddr} 的写法随 Servlet 栈一起失效
 * （Netty 下对端地址是 {@link InetSocketAddress}，不是字符串）。本类改用两种口径：
 * <ul>
 *   <li><b>直接调用</b>控制器方法 + {@link ServerHttpRequestDecorator} 定制对端地址——准入逻辑本身；</li>
 *   <li>一次 {@link WebTestClient} 调用——证明 {@code ResponseStatusException} 仍被框架映射成 HTTP 状态码。</li>
 * </ul>
 * </p>
 */
class OutageAdminControllerTest {

    private static final String TOKEN_HEADER = "X-Knot-Admin-Token";

    private TargetOutageStore store;
    private OutageKeys keys;
    private OutageAdminProperties properties;
    private OutageAdminController controller;

    @BeforeEach
    void setUp() {
        store = new CaffeineTargetOutageStore(1_000L, 60_000L);
        keys = new OutageKeys("knot:outage");
        properties = new OutageAdminProperties();
        controller = new OutageAdminController(store, keys, properties);
    }

    @Test
    @DisplayName("无令牌时 loopback 放行；列表只回非健康条目")
    void listReturnsUnhealthyOnlyForLoopback() {
        // 计数未达阈值（默认 5）：不算非健康，列表必须为空
        store.recordAnomaly(keys.of("rule-a", "gpt-4o"), EjectionPolicy.DEFAULT,
                AnomalyKind.FAILURE, System.currentTimeMillis());

        assertTrue(controller.list(null, null, loopback()).isEmpty(), "未达阈值不应出现在管理面");

        OutageAdminAck ack = controller.eject(
                new OutageAdminEjectRequest("rule-a", "gpt-4o", 120, null), loopback());
        assertEquals("rule-a", ack.ruleCode());
        assertEquals("gpt-4o", ack.modelCode());
        assertEquals("eject", ack.action());
        assertTrue(ack.ejected());
        assertEquals(OutageState.REASON_MANUAL, ack.reason());

        List<OutageAdminItem> items = controller.list(null, null, loopback());
        assertEquals(1, items.size());
        assertEquals("rule-a", items.get(0).ruleCode());
        assertEquals("gpt-4o", items.get(0).modelCode());
        assertEquals(OutageState.REASON_MANUAL, items.get(0).reason());
        assertEquals(0, items.get(0).failures());
    }

    @Test
    @DisplayName("列表按 ruleCode / modelCode 过滤")
    void listFiltersByRuleAndModel() {
        eject("rule-a", "gpt-4o");
        eject("rule-b", "claude");
        eject("rule-a", "claude");

        assertEquals(2, controller.list("rule-a", null, loopback()).size());
        List<OutageAdminItem> narrowed = controller.list("rule-a", "claude", loopback());
        assertEquals(1, narrowed.size());
        assertEquals("claude", narrowed.get(0).modelCode());
        assertTrue(controller.list(null, "no-such", loopback()).isEmpty());
    }

    @Test
    @DisplayName("手动恢复后条目消失")
    void releaseClearsEntry() {
        eject("rule-a", "gpt-4o");

        OutageAdminAck ack = controller.release(new OutageAdminReleaseRequest("rule-a", "gpt-4o"), loopback());
        assertEquals("release", ack.action());
        assertFalse(ack.ejected());

        assertTrue(controller.list(null, null, loopback()).isEmpty());
    }

    @Test
    @DisplayName("配了令牌：缺头/错头 403，对头放行（且不再看来源地址）")
    void tokenGuardsAccess() {
        properties.setToken("s3cret");

        assertForbidden(() -> controller.list(null, null, loopback()));
        assertForbidden(() -> controller.list(null, null,
                remote("127.0.0.1", TOKEN_HEADER, "wrong")));

        List<OutageAdminItem> items = controller.list(null, null, remote("10.0.0.5", TOKEN_HEADER, "s3cret"));
        assertTrue(items.isEmpty(), "令牌校验通过后不再做 loopback 限制");
    }

    @Test
    @DisplayName("未配令牌：非 loopback 一律 403，loopback（含 IPv6）放行")
    void nonLoopbackDeniedWithoutToken() {
        assertForbidden(() -> controller.list(null, null, remote("10.0.0.5")));

        controller.list(null, null, remote("127.0.0.1"));
        controller.list(null, null, remote("::1"));
    }

    @Test
    @DisplayName("对端地址缺失按非 loopback 处理（宁可拒绝也不放宽准入）")
    void missingRemoteAddressIsNotLoopback() {
        ServerHttpRequest noRemote = MockServerHttpRequest.get("/_admin/outages").build();

        assertForbidden(() -> controller.list(null, null, noRemote));
    }

    @Test
    @DisplayName("手动摘除参数缺失 400")
    void ejectRejectsBlankFields() {
        assertBadRequest(() -> controller.eject(new OutageAdminEjectRequest("", "gpt-4o", null, null), loopback()));
        assertBadRequest(() -> controller.eject(new OutageAdminEjectRequest("rule-a", null, null, null), loopback()));
    }

    @Test
    @DisplayName("手动摘除默认冷却 60s，回执带剩余冷却")
    void ejectUsesDefaultCooldown() {
        OutageAdminAck ack = controller.eject(
                new OutageAdminEjectRequest("rule-a", "gpt-4o", null, null), loopback());

        long remaining = ack.ejectedUntil() - System.currentTimeMillis();
        assertTrue(remaining > 50_000L && remaining <= 60_000L, "默认冷却应约 60s，实际剩余 " + remaining + "ms");
    }

    @Test
    @DisplayName("ResponseStatusException 仍被框架映射成 HTTP 403")
    void forbiddenSurfacesAsHttp403() {
        properties.setToken("s3cret");

        WebTestClient.bindToController(controller).build()
                .get().uri("/_admin/outages")
                .exchange()
                .expectStatus().isForbidden();
    }

    private void eject(String ruleCode, String modelCode) {
        controller.eject(new OutageAdminEjectRequest(ruleCode, modelCode, 300, null), loopback());
    }

    private static void assertForbidden(org.junit.jupiter.api.function.Executable call) {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable call) {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, call);
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    private static ServerHttpRequest loopback() {
        return remote("127.0.0.1");
    }

    /**
     * 构造带指定对端地址的请求：Netty 下 loopback 判定看的是 {@link InetSocketAddress}，
     * mock 请求默认没有对端地址，故用装饰器注入。
     */
    private static ServerHttpRequest remote(String host) {
        return remote(host, null, null);
    }

    private static ServerHttpRequest remote(String host, String headerName, String headerValue) {
        MockServerHttpRequest request = headerName == null
                ? MockServerHttpRequest.get("/_admin/outages").build()
                : MockServerHttpRequest.get("/_admin/outages").header(headerName, headerValue).build();
        InetSocketAddress address = new InetSocketAddress(host, 51_234);
        return new ServerHttpRequestDecorator(request) {
            @Override
            public InetSocketAddress getRemoteAddress() {
                return address;
            }
        };
    }
}
