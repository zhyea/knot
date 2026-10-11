package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.exception.GatewayQuotaException;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayTrafficRejectException;
import org.chobit.knot.gateway.model.TrafficRejectDetail;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.traffic.TrafficDecision;
import org.chobit.knot.gateway.traffic.TrafficRejectReason;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 限流 / 限额拒绝的对外契约：429 状态码 + 响应体原因说明 + {@code Retry-After} 头。
 *
 * <p>过去限额类异常没有处理器，会掉进兜底的 {@code Exception} 分支返回 500，
 * 这组用例把「限流与限额同为 429」钉死。</p>
 */
class GatewayTrafficRejectResponseTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void rateLimitRejectionReturns429WithDetailAndRetryAfter() throws Exception {
        MvcResult result = perform(() -> reject(TrafficRejectReason.RATE_LIMIT_RPM, "MODEL", 7L, 2L, 3L, null));

        assertEquals(429, result.getResponse().getStatus());
        assertEquals("42", result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"type\":\"rate_limit_error\""), body);
        assertTrue(body.contains("\"code\":\"RATE_LIMIT_EXCEEDED\""), body);
        assertTrue(body.contains("Rate limit exceeded for MODEL#7"), body);
        assertTrue(body.contains("\"dimension\":\"RPM\""), body);
        assertTrue(body.contains("\"unit\":\"requests/minute\""), body);
        assertTrue(body.contains("\"used\":3"), body);
        assertTrue(body.contains("\"limit\":2"), body);
        assertTrue(body.contains("\"resetAfterSeconds\":42"), body);
    }

    @Test
    void quotaRejectionAlsoReturns429() throws Exception {
        MvcResult result = perform(() -> reject(TrafficRejectReason.QUOTA_COST, "PROVIDER", 99L,
                1_000_000_000L, 1_200_000_000L, "USD"));

        assertEquals(429, result.getResponse().getStatus(), "限额超支必须走 429，不能掉进兜底的 500");
        assertEquals("3600", result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("\"type\":\"quota_error\""), body);
        assertTrue(body.contains("\"code\":\"QUOTA_EXCEEDED\""), body);
        assertTrue(body.contains("\"dimension\":\"COST\""), body);
        assertTrue(body.contains("\"unit\":\"USD\""), body);
        assertTrue(body.contains("Quota exceeded for PROVIDER#99"), body);
    }

    /**
     * 用一个真实的控制器 + 真实的 {@link GatewayExceptionHandler} 跑一遍，
     * 这样 {@code @ResponseStatus} 与 {@code HttpServletResponse} 注入都走 Spring 的实际路径。
     */
    private static MvcResult perform(Supplier<GatewayTrafficRejectException> rejecter) throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new RejectController(rejecter))
                .setControllerAdvice(new GatewayExceptionHandler())
                .build();
        return mvc.perform(get("/reject"))
                .andExpect(status().isTooManyRequests())
                .andReturn();
    }

    private static GatewayTrafficRejectException reject(TrafficRejectReason reason,
                                                       String resourceType,
                                                       Long resourceId,
                                                       long limit,
                                                       long used,
                                                       String currency) {
        long resetAt = reason == TrafficRejectReason.RATE_LIMIT_RPM ? NOW + 42_000L : NOW + 3_600_000L;
        TrafficDecision decision = TrafficDecision.reject(reason, resourceType, resourceId,
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
