package org.chobit.knot.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.GatewayErrorResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上游错误响应契约（Batch4 冒烟场景 7 副产物）。
 *
 * <p>网关对上游非 2xx 统一以 {@code 502} 回给客户端（错误码隔离），HTTP 状态码本身不变。
 * 但如果连上游原状态码一起丢弃，调用方就分不清「余额不足 402」「限流 429」与「真故障」，
 * 管理端计费裁判因此只能报「网关错误 502」。本契约要求：502 响应体必须带上 {@code upstreamStatus}
 * 与截断后的 {@code upstreamMessage}，供有诊断需求的调用方还原语义。</p>
 *
 * <p>对既有调用方是纯新增字段（Jackson 忽略未知字段），向后兼容。</p>
 */
class GatewayUpstreamErrorResponseTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    @Test
    void upstreamFailureCarriesOriginalStatus() {
        GatewayErrorResponse response = handler.handleGatewayUpstreamException(new GatewayUpstreamException(
                "upstream failed", "UPSTREAM_ERROR", 402, "{\"error\":{\"message\":\"Insufficient credits\"}}")).getBody();

        assertEquals(402, response.error().upstreamStatus());
        assertEquals("{\"error\":{\"message\":\"Insufficient credits\"}}", response.error().upstreamMessage());
        assertEquals("UPSTREAM_ERROR", response.error().code());
        assertEquals("upstream failed", response.error().message());
    }

    @Test
    void upstreamBodyTruncatedToBoundedSummary() {
        String huge = "x".repeat(5000);
        GatewayErrorResponse response = handler.handleGatewayUpstreamException(new GatewayUpstreamException(
                "boom", "UPSTREAM_ERROR", 429, huge)).getBody();

        assertEquals(429, response.error().upstreamStatus());
        String summary = response.error().upstreamMessage();
        assertTrue(summary.length() <= 301, "上游响应体必须截断，长度=" + summary.length());
        assertTrue(summary.endsWith("…"), "截断应有省略号标识");
    }

    @Test
    void upstreamFailureWithoutDetailKeepsNulls() {
        GatewayErrorResponse response = handler.handleGatewayUpstreamException(
                new GatewayUpstreamException("boom", "UPSTREAM_ERROR")).getBody();

        assertNull(response.error().upstreamStatus());
        assertNull(response.error().upstreamMessage());
        assertNull(response.error().detail(), "非拒绝类错误不带 detail");
    }

    @Test
    void serializedErrorBodyUsesCamelCaseUpstreamFields() throws Exception {
        // JevBillingJudge.readUpstreamStatus 依赖 error.upstreamStatus 这条路径，字段名必须与之一致
        GatewayErrorResponse response = handler.handleGatewayUpstreamException(new GatewayUpstreamException(
                "upstream failed", "UPSTREAM_ERROR", 402, "boom")).getBody();
        Map<?, ?> json = new ObjectMapper().readValue(
                new ObjectMapper().writeValueAsString(response), Map.class);
        Map<?, ?> error = (Map<?, ?>) json.get("error");
        assertEquals(402, error.get("upstreamStatus"));
        assertEquals("boom", error.get("upstreamMessage"));
    }

    @Test
    void httpStatusStaysBadGateway() {
        // 契约核心：HTTP 状态码保持 502 不变，语义只通过响应体新增字段传递。
        // 2026-10-20（WebFlux 批2）：状态码不再靠 @ResponseStatus 注解读，改由 ResponseEntity 带出。
        var response = handler.handleGatewayUpstreamException(
                new GatewayUpstreamException("upstream failed", "UPSTREAM_ERROR", 402, "boom"));

        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, response.getStatusCode(),
                "上游 402 仍然期望回 502：状态码隔离是既有约定，402 只进响应体");
    }

}
