package org.chobit.knot.gateway.runtime;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * traceparent 默认填充：接口调用未携带（或携带非法）traceparent 时，
 * 网关必须生成符合 W3C 规范的默认值，而非拒绝请求或透传空值。
 *
 * <p>该默认逻辑由 {@link GatewayTraceFilter}（HIGHEST_PRECEDENCE）在请求入口调用
 * {@link GatewayTraceContext#fromTraceparent(String)} 触发，并借 {@link GatewayTraceContext#currentOrCreate(String)}
 * 贯穿到下游转发与响应头回写。控制器侧已统一用 {@code required = false} 接收头，
 * 使「缺失」这一分支可达。</p>
 */
class GatewayTraceContextTest {

    private static final Pattern W3C = Pattern.compile(
            "^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

    @Test
    void generatesDefaultWhenHeaderAbsent() {
        GatewayTraceContext ctx = GatewayTraceContext.fromTraceparent(null);
        assertGeneratedDefault(ctx);
        assertTrue(ctx.generatedTraceId(), "缺失头应标记为网关生成的 traceId");
    }

    @Test
    void generatesDefaultWhenHeaderBlank() {
        for (String blank : new String[]{"", "   ", "\t"}) {
            GatewayTraceContext ctx = GatewayTraceContext.fromTraceparent(blank);
            assertGeneratedDefault(ctx);
        }
    }

    @Test
    void generatesDefaultWhenHeaderMalformed() {
        GatewayTraceContext ctx = GatewayTraceContext.fromTraceparent("not-a-traceparent");
        assertGeneratedDefault(ctx);
        assertTrue(ctx.generatedTraceId());
    }

    @Test
    void preservesTraceIdAndFlagsForValidHeader() {
        String incoming = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        GatewayTraceContext ctx = GatewayTraceContext.fromTraceparent(incoming);

        assertEquals("00", ctx.version());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ctx.traceId());
        assertEquals("01", ctx.traceFlags());
        assertFalse(ctx.generatedTraceId(), "合法头不应标记为网关生成");
        // 网关用自身新生成的 spanId 覆盖 parentId，作为对下游传播的 traceparent
        var m = W3C.matcher(ctx.traceparent());
        assertTrue(m.matches(), "派生 traceparent 必须合规: " + ctx.traceparent());
        assertEquals("00", m.group(1));
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", m.group(2)); // traceId 保留
        assertEquals("01", m.group(4));
        assertNotEquals("00f067aa0ba902b7", m.group(3), "下游 traceparent 应使用网关新 spanId");
        assertEquals(incoming, ctx.originalTraceparent());
    }

    @Test
    void fromNullGeneratesDefault() {
        assertGeneratedDefault(GatewayTraceContext.fromTraceparent(null));
    }

    @Test
    void generatedDefaultsAreUnique() {
        String a = GatewayTraceContext.fromTraceparent(null).traceparent();
        String b = GatewayTraceContext.fromTraceparent(null).traceparent();
        assertNotEquals(a, b, "两次缺失都应生成不同的 traceparent");
    }

    // ---------------------------------------------------------------------
    // 网关自生成的 request_id（2026-10-23）：与 traceId 是两个 id，不可互相替代
    // ---------------------------------------------------------------------

    @Test
    void requestIdIsGeneratedAs32LowerHex() {
        GatewayTraceContext ctx = GatewayTraceContext.fromTraceparent(null);

        assertNotNull(ctx.requestId(), "入口必须生成 request_id");
        assertTrue(ctx.requestId().matches("[0-9a-f]{32}"),
                "请求标识统一 32 位小写 hex 形态: " + ctx.requestId());
    }

    @Test
    void requestIdIsGatewayOwnEvenWhenInboundTraceparentPresent() {
        // 入站 traceparent 的 traceId 属于调用方；requestId 必须是我方新生成的编号
        String incoming = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        GatewayTraceContext ctx = GatewayTraceContext.fromTraceparent(incoming);

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ctx.traceId());
        assertNotEquals(ctx.traceId(), ctx.requestId(),
                "request_id 不得复用入站 traceId（那是别人的链路 id）");
        assertTrue(ctx.requestId().matches("[0-9a-f]{32}"));
    }

    @Test
    void requestIdIsUniquePerRequest() {
        String first = GatewayTraceContext.fromTraceparent(null).requestId();
        String second = GatewayTraceContext.fromTraceparent(null).requestId();

        assertNotEquals(first, second, "两次请求必须拿到不同的 request_id");
    }

    @Test
    void requestIdNeverLeaksIntoOutboundTraceparent() {
        GatewayTraceContext ctx = GatewayTraceContext.fromTraceparent(null);

        assertFalse(ctx.traceparent().contains(ctx.requestId()),
                "request_id 不是 W3C 字段，不能混进 traceparent");
    }

    private static void assertGeneratedDefault(GatewayTraceContext ctx) {        assertNotNull(ctx);
        var m = W3C.matcher(ctx.traceparent());
        assertTrue(m.matches(), "生成的默认 traceparent 必须合规: " + ctx.traceparent());
        assertEquals("00", m.group(1));
        assertEquals("01", m.group(4));
        assertFalse(isAllZero(m.group(2)), "生成的 traceId 不应全 0");
        assertFalse(isAllZero(m.group(3)), "生成的 spanId 不应全 0");
    }

    private static boolean isAllZero(String hex) {
        return hex.chars().allMatch(ch -> ch == '0');
    }
}
