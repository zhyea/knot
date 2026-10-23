package org.chobit.knot.gateway.upstream.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上游响应头白名单归一：request-id 优先序、log-id 兜底、snapshot 边界。
 *
 * <p>对应设计文档 {@code docs/上游trace打通-双向对账设计-2026-10-17.md} §4.1：
 * 白名单只收「对账用标识」，禁止全量抓取（防 set-cookie / 鉴权回显泄进数仓与日志）。
 * request-id 类优先，全未命中兜底 log-id 类并视为 request_id（取其一，不并存两个字段）。
 * 头名均经实测确认。</p>
 */
class UpstreamRequestIdResolverTest {

    @Test
    void requestIdFollowsPriorityOrder() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("request-id", "req_low");
        headers.add("apim-request-id", "req_azure");
        headers.add("x-request-id", "req_openai");

        assertEquals("req_openai", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void requestIdFallsBackWhenPriorityHeadMissing() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("request-id", "req_anthropic");
        headers.add("apim-request-id", "req_azure");

        assertEquals("req_anthropic", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void requestIdFallsBackToAliyunHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-acs-request-id", "req_dashscope");

        assertEquals("req_dashscope", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void requestIdReadsTencentCloudHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-tc-request-id", "req_tencent_abc123");

        assertEquals("req_tencent_abc123", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void requestIdReadsMoonshotHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("msh-request-id", "req_moonshot_xyz789");

        assertEquals("req_moonshot_xyz789", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void requestIdPrefersOpenAiHeaderOverVendorSpecific() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-tc-request-id", "req_tencent");
        headers.add("msh-request-id", "req_moonshot");
        headers.add("x-request-id", "req_openai");

        assertEquals("req_openai", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void requestIdPrefersRequestIdOverLogId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tt-Logid", "20241015xxxx-logid");
        headers.add("cf-ray", "8a1b2c3d4-LAX");
        headers.add("x-request-id", "req_openai");

        assertEquals("req_openai", UpstreamRequestIdResolver.requestId(headers, null),
                "request-id 类命中时不得回退取 log-id（取其一，优先 request-id）");
    }

    @Test
    void requestIdFallsBackToByteSeriesLogIdWhenNoRequestId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tt-Logid", "20241015xxxx-logid");

        assertEquals("20241015xxxx-logid", UpstreamRequestIdResolver.requestId(headers, null),
                "request-id 类全未命中时兜底取 X-Tt-Logid 并视为 request_id");
    }

    @Test
    void requestIdFallsBackToCfRayWhenNoRequestIdOrByteLogId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("cf-ray", "8a1b2c3d4-LAX");

        assertEquals("8a1b2c3d4-LAX", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void blankHeadIsSkippedNotReturnedAsEmpty() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "   ");
        headers.add("request-id", "req_anthropic");

        assertEquals("req_anthropic", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void requestIdTrimsSurroundingWhitespace() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "  req_padded  ");

        assertEquals("req_padded", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void headerLookupIsCaseInsensitive() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Request-ID", "req_upper");

        assertEquals("req_upper", UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void requestIdReturnsNullWhenNoWhitelistHit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("set-cookie", "session=secret");
        headers.add("authorization", "Bearer sk-xxx");
        headers.add("content-type", "application/json");

        assertNull(UpstreamRequestIdResolver.requestId(headers, null));
    }

    @Test
    void nullHeadersYieldNullIds() {
        assertNull(UpstreamRequestIdResolver.requestId(null, null));
        assertTrue(UpstreamRequestIdResolver.snapshot(null, null).isEmpty());
    }

    @Test
    void snapshotContainsOnlyWhitelistedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "req_openai");
        headers.add("set-cookie", "session=secret");
        headers.add("authorization", "Bearer sk-xxx");
        headers.add("content-type", "application/json");
        headers.add("x-envoy-upstream-service-time", "37");

        Map<String, String> snapshot = UpstreamRequestIdResolver.snapshot(headers, null);

        assertEquals(1, snapshot.size(), "只允许白名单命中进快照（敏感头/无关头一律不进）");
        assertEquals("req_openai", snapshot.get("x-request-id"));
    }

    @Test
    void snapshotCarriesLogIdFallbackHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Tt-Logid", "20241015xxxx-logid");
        headers.add("cf-ray", "8a1b2c3d4-LAX");

        Map<String, String> snapshot = UpstreamRequestIdResolver.snapshot(headers, null);

        assertEquals(2, snapshot.size(), "log-id 类头属白名单兜底档，同样进快照");
        assertEquals("20241015xxxx-logid", snapshot.get("x-tt-logid"));
        assertEquals("8a1b2c3d4-LAX", snapshot.get("cf-ray"));
    }

    @Test
    void snapshotCarriesEchoedTraceparent() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        Map<String, String> snapshot = UpstreamRequestIdResolver.snapshot(headers, null);

        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", snapshot.get("traceparent"));
    }

    @Test
    void snapshotIsEmptyWhenNothingHits() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

        assertTrue(UpstreamRequestIdResolver.snapshot(headers, null).isEmpty());
    }

    // ---------------------------------------------------------------------
    // 我方 request_id 的回显排除（2026-10-23）：
    // 网关把自生成的 request_id 以 X-Request-Id 发给上游，部分上游/反代会原样回显。
    // 若不排除，upstream_request_id 会取到我方 id 冒充供应商侧编号，双向对账失效。
    // ---------------------------------------------------------------------

    @Test
    void echoedOwnRequestIdIsNotReturnedAsUpstreamId() {
        String own = "9f2c7d1e4b6a48c093e5f0a1b2c3d4e5";
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", own);

        assertNull(UpstreamRequestIdResolver.requestId(headers, own),
                "上游只是回显了我方 X-Request-Id，不能当作 upstream_request_id");
    }

    @Test
    void echoedOwnRequestIdFallsThroughToNextHeader() {
        String own = "9f2c7d1e4b6a48c093e5f0a1b2c3d4e5";
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", own);
        headers.add("request-id", "req_anthropic");

        assertEquals("req_anthropic", UpstreamRequestIdResolver.requestId(headers, own),
                "跳过回显值后必须继续按优先序找真实上游 id，不得直接返回 null");
    }

    @Test
    void echoMatchIsCaseInsensitive() {
        // 我方发小写 hex，上游可能规范化成大写再回显
        String own = "9f2c7d1e4b6a48c093e5f0a1b2c3d4e5";
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", own.toUpperCase());

        assertNull(UpstreamRequestIdResolver.requestId(headers, own));
    }

    @Test
    void snapshotOmitsEchoedOwnRequestId() {
        String own = "9f2c7d1e4b6a48c093e5f0a1b2c3d4e5";
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", own);
        headers.add("x-tc-request-id", "req_tencent_real");

        Map<String, String> snapshot = UpstreamRequestIdResolver.snapshot(headers, own);

        assertFalse(snapshot.containsKey("x-request-id"), "回显值不得进快照，避免被读成上游编号");
        assertEquals("req_tencent_real", snapshot.get("x-tc-request-id"));
    }

    @Test
    void nullOwnRequestIdKeepsEveryHit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-request-id", "req_openai");

        assertEquals("req_openai", UpstreamRequestIdResolver.requestId(headers, null),
                "未提供我方 id 时不做排除（等价于历史行为）");
        assertTrue(UpstreamRequestIdResolver.snapshot(headers, null).containsKey("x-request-id"));
    }
}
