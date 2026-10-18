package org.chobit.knot.gateway.kafka;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.kafka.msg.ResponseMsgDto;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.TrafficRejectDetail;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.redis.RedisUnavailableException;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 响应层消息的组装口径：<b>成功与失败同形状，失败靠 error 区分；null 与 0 不许混。</b>
 */
class ResponseMsgDtoTest {

    @Test
    void successCarriesUsageAndHitModel() {
        GatewayRequestContext context = context();
        context.putAttribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE, target("gpt-4o-mini", 2, false));

        ResponseMsgDto message = ResponseMsgDto.of(
                context, trace(), 812L, usage(), null);

        assertEquals("success", message.status());
        assertEquals(200, message.httpStatus());
        assertNull(message.error());
        assertEquals(812L, message.ttftMs());
        assertEquals("gpt-4o-mini", message.hit().modelCode());
        assertEquals("upstream-gpt-4o-mini", message.hit().upstreamModelCode());
        assertNotNull(message.usage(), "成功路径必须带归一化用量");
        assertEquals("0.00150000", message.billing().totalCost(), "金额是字符串且不出科学计数法");
        assertEquals("output", message.billing().detail().get(0).type());
    }

    @Test
    void upstreamFailureIsCarriedAsStructuredError() {
        GatewayRequestContext context = context();
        GatewayUpstreamException error = new GatewayUpstreamException(
                "upstream responded with status 503", "UPSTREAM_ERROR", 503, "{\"error\":\"busy\"}", 1500L);

        ResponseMsgDto message = ResponseMsgDto.of(
                context, trace(), null, null, error);

        assertEquals("upstream_error", message.status());
        assertEquals(502, message.httpStatus());
        assertNull(message.ttftMs(), "没有成功首 token，必须是 null 而不是 0");
        assertNull(message.hit(), "没有成功上游调用，hit 必须是 null");
        assertEquals("upstream_error", message.error().type());
        assertEquals("upstream_call", message.error().stage());
        assertEquals(503, message.error().upstream().httpStatus());
        assertEquals(1500L, message.error().upstream().retryAfterMs());
    }

    @Test
    void rateLimitRejectionCarriesRejectDetail() {
        GatewayRequestContext context = context();
        TrafficRejectDetail detail = new TrafficRejectDetail(
                "MODEL", "gpt-4o-mini", "RATE_LIMIT_TPM", "TPM", "tokens/minute",
                BigDecimal.valueOf(100000), BigDecimal.valueOf(100432), "2026-10-17T07:13:00Z", 27L);
        GatewayRateLimitException error = new GatewayRateLimitException(
                "Rate limit exceeded", "RATE_LIMIT_EXCEEDED", detail);

        ResponseMsgDto message = ResponseMsgDto.of(
                context, null, null, null, error);

        assertEquals("rate_limited", message.status());
        assertEquals(429, message.httpStatus());
        assertEquals("rate_limit_error", message.error().type());
        // MODEL / PROVIDER 属目标级检查；APP / 规则 / 消费者属规则级检查
        assertEquals("target_traffic_check", message.error().stage());
        assertEquals("gpt-4o-mini", message.error().detail().resourceCode());
        assertEquals("TPM", message.error().detail().dimension());
    }

    @Test
    void storageUnavailableIsNotUpstreamError() {
        GatewayRequestContext context = context();

        ResponseMsgDto message = ResponseMsgDto.of(context, null, null, null,
                new RedisUnavailableException("traffic", "incr", "k:1", null));

        assertEquals("gateway_error", message.status());
        assertEquals(503, message.httpStatus());
        assertEquals("storage_unavailable", message.error().type());
        assertEquals("storage", message.error().stage());
    }

    @Test
    void traceIsFlattenedIntoResponseMessage() {
        GatewayRequestContext context = context();
        CallTraceContext trace = new CallTraceContext();
        CallTraceContext.AttemptDraft first = trace.newAttempt(target("gpt-4o", 1, true), null);
        first.nextAttempt(2);
        first.failed(new GatewayUpstreamException("boom", "UPSTREAM_ERROR", 500, null, null), true);
        CallTraceContext.AttemptDraft second = trace.newAttempt(target("gpt-4o-mini", 2, false), null);
        second.nextAttempt(2);
        second.succeeded();

        ResponseMsgDto message = ResponseMsgDto.of(context, trace, 100L, null, null);

        assertEquals(2, message.attempts().size(), "attempt 明细内嵌在响应层，不再独立成表");
        assertEquals("gpt-4o", message.attempts().get(0).targetCode());
        assertEquals("upstream_failure", message.attempts().get(0).outcome());
        assertTrue(message.attempts().get(0).countedForEjection());
        assertEquals("success", message.attempts().get(1).outcome());
        assertEquals(1, message.failoverTotal(), "成功前失败过一个候选");
        assertEquals(0, message.ejectedSkipped());
    }

    @Test
    void serializedJsonKeepsCostAsString() {
        GatewayRequestContext context = context();
        ResponseMsgDto message = ResponseMsgDto.of(
                context, null, 1L, usage(), null);

        String json = JsonKit.toJson(message);

        assertTrue(json.contains("\"total_cost\":\"0.00150000\""), "成本必须是字符串形态: " + json);
        assertTrue(json.contains("\"cost\":\"0.00150000\""));
    }

    private static GatewayRequestContext context() {
        return new GatewayRequestContext("Bearer key", "rule-a", GatewayTraceContext.fromTraceparent(null), null, null);
    }

    private static CallTraceContext trace() {
        return new CallTraceContext();
    }

    private static RoutingRuleTargetDto target(String code, int priority, boolean primary) {
        return new RoutingRuleTargetDto("MODEL", 1L, code, "upstream-" + code, code,
                "CHAT", "PA-1", priority, primary);
    }

    /**
     * 用量账目：上游原始 usage + 计费输入 + 计费结果三段齐全。
     */
    private static UsageAccounting usage() {
        Map<String, Object> raw = Map.of("prompt_tokens", 3, "completion_tokens", 2, "total_tokens", 5);
        NormalizedUsage normalized = new NormalizedUsage(5L, "0.00150000", "CNY", "v1",
                List.of(new NormalizedUsageDetail("output", 2L, "0.00150000", "4.00000000")));
        return UsageAccounting.of(raw, null, TokenBillingUsage.from(raw), normalized);
    }
}
