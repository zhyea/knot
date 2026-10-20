package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.exception.GatewayQuotaException;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayTrafficRejectException;
import org.chobit.knot.gateway.model.TrafficRejectDetail;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.traffic.TrafficDecision;
import org.chobit.knot.gateway.traffic.TrafficRejectReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 限流 / 限额拒绝的对外契约：429 状态码 + 响应体原因说明 + {@code Retry-After} 头。
 *
 * <p>过去限额类异常没有处理器，会掉进兜底的 {@code Exception} 分支返回 500，
 * 这组用例把「限流与限额同为 429」钉死。</p>
 *
 * <p>2026-10-20（WebFlux 批2）：{@code MockMvc}（Servlet 栈）换成 {@link WebTestClient}。
 * 这一层顺带钉住另一件事：{@code Retry-After} 现在只能靠 {@code ResponseEntity} 带出，
 * 没有 {@code HttpServletResponse} 可以就地改头了。</p>
 */
class GatewayTrafficRejectResponseTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    @DisplayName("限流拒绝：429 + Retry-After + detail 结构")
    void rateLimitRejectionReturns429WithDetailAndRetryAfter() {
        assertReject(() -> reject(TrafficRejectReason.RATE_LIMIT_RPM, "MODEL", "gpt-4o", 2L, 3L, null),
                "42", body -> {
                    assertTrue(body.contains("\"type\":\"rate_limit_error\""), body);
                    assertTrue(body.contains("\"code\":\"RATE_LIMIT_EXCEEDED\""), body);
                    assertTrue(body.contains("Rate limit exceeded for MODEL#gpt-4o"), body);
                    assertTrue(body.contains("\"resourceCode\":\"gpt-4o\""), body);
                    assertFalse(body.contains("resourceId"), body);
                    assertTrue(body.contains("\"dimension\":\"RPM\""), body);
                    assertTrue(body.contains("\"unit\":\"requests/minute\""), body);
                    assertTrue(body.contains("\"used\":3"), body);
                    assertTrue(body.contains("\"limit\":2"), body);
                    assertTrue(body.contains("\"resetAfterSeconds\":42"), body);
                });
    }

    @Test
    @DisplayName("限额拒绝同样 429，不能掉进兜底的 500")
    void quotaRejectionAlsoReturns429() {
        assertReject(() -> reject(TrafficRejectReason.QUOTA_COST, "PROVIDER", "account-code",
                        10_000_000_000_000L, 12_000_000_000_000L, "USD"),
                "3600", body -> {
                    assertTrue(body.contains("\"type\":\"quota_error\""), body);
                    assertTrue(body.contains("\"code\":\"QUOTA_EXCEEDED\""), body);
                    assertTrue(body.contains("\"dimension\":\"COST\""), body);
                    assertTrue(body.contains("\"unit\":\"USD\""), body);
                    assertTrue(body.contains("Quota exceeded for PROVIDER#account-code"), body);
                    assertTrue(body.contains("\"resourceCode\":\"account-code\""), body);
                    assertFalse(body.contains("resourceId"), body);
                });
    }

    /**
     * 用一个真实的控制器 + 真实的 {@link GatewayExceptionHandler} 跑一遍，
     * 这样 {@code ResponseEntity} 的状态码与响应头都走 Spring 的实际路径。
     */
    private static void assertReject(Supplier<GatewayTrafficRejectException> rejecter,
                                     String expectedRetryAfter,
                                     java.util.function.Consumer<String> bodyAssertions) {
        client(rejecter).get().uri("/reject").exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, expectedRetryAfter)
                .expectBody(String.class)
                .value(bodyAssertions);
    }

    private static WebTestClient client(Supplier<GatewayTrafficRejectException> rejecter) {
        return WebTestClient.bindToController(new RejectController(rejecter))
                .controllerAdvice(new GatewayExceptionHandler())
                .build();
    }

    private static GatewayTrafficRejectException reject(TrafficRejectReason reason,
                                                        String resourceType,
                                                        String resourceCode,
                                                        long limit,
                                                        long used,
                                                        String currency) {
        long resetAt = reason == TrafficRejectReason.RATE_LIMIT_RPM ? NOW + 42_000L : NOW + 3_600_000L;
        TrafficDecision decision = TrafficDecision.reject(reason, resourceType, resourceCode,
                limit, used, resetAt, currency);
        TrafficRejectDetail detail = TrafficRejectDetail.of(decision, NOW);
        String code = reason.errorCode().code();
        return reason.quota()
                ? new GatewayQuotaException(detail.describe(), code, detail)
                : new GatewayRateLimitException(detail.describe(), code, detail);
    }

    @RestController
    static class RejectController extends GatewayControllerSupport {

        private final Supplier<GatewayTrafficRejectException> rejecter;

        RejectController(Supplier<GatewayTrafficRejectException> rejecter) {
            super((GatewayRequestHandler) null);
            this.rejecter = rejecter;
        }

        @GetMapping("/reject")
        public Object reject() {
            throw rejecter.get();
        }
    }
}
