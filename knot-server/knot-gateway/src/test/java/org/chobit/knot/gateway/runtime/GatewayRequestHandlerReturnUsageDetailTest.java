package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.model.usage.KnotExtendPayload;
import org.chobit.knot.gateway.usage.ModelUsageNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「返回消费详情」注入判定：消费者配置 OR 管理面测试头（{@code X-Knot-Test-Return-Usage}）。
 *
 * <p>测试头是管理端调试抽屉「消费者没开返回消费详情时临时开启」的落地机制：
 * 带头仅让本次调用多出自身用量账目，不改变正式流量形态。</p>
 *
 * <p>2026-10-20（WebFlux 批1-B）：测试头读取入口由 {@code RequestContextHolder}
 * 改为 {@link GatewayHttpExchange} 显式载体——判定可能发生在异步线程上，
 * 读线程本地的请求属性在那里必然为空。</p>
 */
class GatewayRequestHandlerReturnUsageDetailTest {

    private static final String RESPONSE_BODY = "{\"id\":\"chat-1\",\"choices\":[]}";

    private GatewayRequestHandler handler;

    @BeforeEach
    void setUp() {
        // applyUsageAccounting 路径不触达任何协作者（仅读 exchange 与 context），故可全 null 构造；
        // 末尾参数是埋点业务侧维护的主题全名（本路径不发布事件，传 null 即可）
        handler = new GatewayRequestHandler(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    @Test
    @DisplayName("头值解析：仅 true（忽略大小写与空白）生效")
    void headerValueParsing() {
        assertTrue(GatewayRequestHandler.isTestReturnUsageHeader("true"));
        assertTrue(GatewayRequestHandler.isTestReturnUsageHeader("TRUE"));
        assertTrue(GatewayRequestHandler.isTestReturnUsageHeader("  True  "));
        assertFalse(GatewayRequestHandler.isTestReturnUsageHeader(null));
        assertFalse(GatewayRequestHandler.isTestReturnUsageHeader(""));
        assertFalse(GatewayRequestHandler.isTestReturnUsageHeader("false"));
        assertFalse(GatewayRequestHandler.isTestReturnUsageHeader("1"));
        assertFalse(GatewayRequestHandler.isTestReturnUsageHeader("yes"));
    }

    @Test
    @DisplayName("消费者未开 + 无测试头：响应体原样返回")
    void consumerOffWithoutHeaderKeepsBody() {
        Object body = applyNonStreaming(false, null);

        assertSame(RESPONSE_BODY, body, "两个开关都不开时不得改动响应体");
    }

    @Test
    @DisplayName("消费者未开 + 测试头 true：注入 knot_extend")
    void consumerOffWithTestHeaderInjects() {
        Object body = applyNonStreaming(false, "true");

        Map<?, ?> injected = assertInstanceOf(Map.class, body, "注入后应返回解析后的响应体 Map");
        assertTrue(injected.containsKey("knot_extend"), "测试头生效必须注入 knot_extend");
    }

    @Test
    @DisplayName("消费者已开 + 无测试头：维持原行为（注入）")
    void consumerOnWithoutHeaderInjects() {
        Object body = applyNonStreaming(true, null);

        Map<?, ?> injected = assertInstanceOf(Map.class, body);
        assertTrue(injected.containsKey("knot_extend"), "消费者配置开启时不受测试头影响");
    }

    @Test
    @DisplayName("消费者已开 + 测试头 false：仍注入（配置优先，头不能关掉已开的配置）")
    void consumerOnWithFalseHeaderStillInjects() {
        Object body = applyNonStreaming(true, "false");

        Map<?, ?> injected = assertInstanceOf(Map.class, body);
        assertTrue(injected.containsKey("knot_extend"));
    }

    @Test
    @DisplayName("测试头非 true 值（含缺失）：不注入")
    void nonTrueHeaderValuesDoNotInject() {
        Object body = applyNonStreaming(false, "enabled");

        assertSame(RESPONSE_BODY, body, "非 true 的头值按未开启处理");
    }

    @Test
    @DisplayName("无 HTTP 载体（非 web 场景）：测试头按未开启处理，不炸链路")
    void withoutHttpCarrierDoesNotThrow() {
        Object body = applyNonStreamingWithoutCarrier(false);

        assertSame(RESPONSE_BODY, body);
    }

    @Test
    @DisplayName("注入内容含 usage 与 billing 两视图")
    void injectedPayloadCarriesBothViews() {
        Object body = applyNonStreaming(false, "true");

        Map<?, ?> injected = assertInstanceOf(Map.class, body);
        KnotExtendPayload extend = assertInstanceOf(KnotExtendPayload.class, injected.get("knot_extend"));
        assertTrue(extend.usage() != null, "usage 视图必须随 knot_extend 注入");
        assertTrue(extend.billing() != null, "billing 视图必须随 knot_extend 注入");
        assertEquals("0.001", extend.billing().totalCost(), "金额契约为十进制普通字符串");
    }

    /**
     * 请求头载体：模拟管理面调试抽屉带上 {@code X-Knot-Test-Return-Usage}（缺失时不加头）。
     */
    private static GatewayHttpExchange carrier(String testHeaderValue) {
        RecordingHttpExchange carrier = new RecordingHttpExchange();
        if (testHeaderValue != null) {
            carrier.requestHeader(GatewayHeaders.X_KNOT_TEST_RETURN_USAGE, testHeaderValue);
        }
        return carrier;
    }

    /**
     * 驱动非流式注入点：消费者开关 + 显式 HTTP 载体里的测试头 → 观察响应体是否被注入。
     */
    private Object applyNonStreaming(boolean consumerReturnUsageDetail, String testHeaderValue) {
        return apply(consumerReturnUsageDetail, carrier(testHeaderValue));
    }

    /**
     * 无 HTTP 载体（{@link GatewayHttpExchange#noop()}）的降级路径。
     */
    private Object applyNonStreamingWithoutCarrier(boolean consumerReturnUsageDetail) {
        return apply(consumerReturnUsageDetail, GatewayHttpExchange.noop());
    }

    private Object apply(boolean consumerReturnUsageDetail, GatewayHttpExchange http) {
        ResolvedRouting routing = new ResolvedRouting(
                1L, "rule-a", 10L, "sk-routing-key", consumerReturnUsageDetail,
                List.of(), null, null, null);
        Map<String, Object> rawUsage = Map.of("prompt_tokens", 3, "completion_tokens", 2, "total_tokens", 5);
        UsageAccounting usage = UsageAccounting.of(
                rawUsage,
                null,
                null,
                ModelUsageNormalizer.fromBilling(TokenBillingUsage.from(rawUsage), null),
                new NormalizedUsage(5L, "0.001", "CNY", "v1", List.of()));
        GatewayExchange exchange = new GatewayExchange(Map.of(), MediaType.APPLICATION_JSON, http);
        exchange.proxyResult(new ProxyResult(RESPONSE_BODY, 1L, 2L, usage));

        GatewayRequestContext context = new GatewayRequestContext(
                "Bearer sk-routing-key", "rule-a", null, null, null);
        context.apiKey("sk-routing-key");
        context.routing(routing);
        return handler.applyUsageAccounting(context, exchange);
    }
}
