package org.chobit.knot.gateway.kafka;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.chobit.knot.gateway.kafka.msg.Msg;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.TrafficRejectDetail;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.redis.RedisUnavailableException;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关流量事件（合并后单条消息）的组装口径：
 * <b>请求入口维度 + 真正请求的模型 + 处置结果同一条消息</b>；成功与失败同形状，失败靠 error 区分；
 * null 与 0 不许混。取代原 RequestMsgDtoTest + ResponseMsgDtoTest（2026-10-18 合并）。
 */
class GatewayTrafficMsgDtoTest {

    @Test
    void recordsActuallyRequestedModelFromHitTarget() {
        GatewayRequestContext context = context();
        context.putAttribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE,
                target("gpt-4o", "gpt-4o-2024-07-18", 1, true));
        context.routing(routing(target("gpt-4o", "gpt-4o-2024-07-18", 1, true),
                target("gpt-4o-mini", "gpt-4o-mini-2024-07-18", 2, false)));

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, null, null);

        assertEquals("success", message.status(), "无异常即成功");
        assertEquals("gpt-4o", message.hit().modelCode(), "统一模型 code 取实际承接模型（kb_models 业务码）");
        assertEquals("gpt-4o-2024-07-18", message.hit().upstreamModelCode(),
                "供应商模型 code 取写向上游请求体的标识");
        assertNull(message.hit().poolCode(), "规则直挂模型没有来源池");
        assertEquals("MODEL", message.routing().targetType());
    }

    @Test
    void hitFromPoolCarriesPoolCode() {
        // 模型池目标展开为池内模型：真正请求的模型带来源池 code，数仓据此归因到池
        GatewayRequestContext context = context();
        context.putAttribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE,
                poolItem("POOL-chat", "gpt-4o", "gpt-4o-2024-07-18", 1, true));
        context.routing(routing(poolItem("POOL-chat", "gpt-4o", "gpt-4o-2024-07-18", 1, true),
                poolItem("POOL-chat", "gpt-4o-mini", "gpt-4o-mini-2024-07-18", 2, false)));

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, null, null);

        assertEquals("gpt-4o", message.hit().modelCode(), "池内被使用的模型");
        assertEquals("gpt-4o-2024-07-18", message.hit().upstreamModelCode());
        assertEquals("POOL-chat", message.hit().poolCode(), "来源池 code 供数仓归因");
        assertEquals("MODEL_POOL", message.routing().targetType(),
                "主目标为模型池：候选 targetType 已被改写为 MODEL，快照按 poolCode 还原");
        assertEquals(2, message.routing().candidateCount());
    }

    @Test
    void failoverHitIsNotFirstCandidate() {
        // 首选被摘除 / 限流顺延到第二个候选：记真正承接的那个，不是候选首位
        GatewayRequestContext context = context();
        context.putAttribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE,
                target("gpt-4o-mini", "gpt-4o-mini-2024-07-18", 2, false));
        context.routing(routing(target("gpt-4o", "gpt-4o-2024-07-18", 1, true),
                target("gpt-4o-mini", "gpt-4o-mini-2024-07-18", 2, false)));

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, null, null);

        assertEquals("gpt-4o-mini", message.hit().modelCode(), "failover 后真正请求的模型");
        assertEquals("gpt-4o-mini-2024-07-18", message.hit().upstreamModelCode());
        assertNull(message.hit().poolCode());
    }

    @Test
    void noHitYieldsNullHit() {
        // 路由解析后的拒绝 / 上游全失败：没有候选真正承接，hit 为 null，路由维度照常记录
        GatewayRequestContext ctx = context();
        ctx.routing(routing(target("gpt-4o", "gpt-4o-2024-07-18", 1, true)));

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(ctx, null, null, null, null);

        assertNull(message.hit());
        assertEquals(1, message.routing().candidateCount(), "路由维度照常记录");
    }

    @Test
    void nullRoutingYieldsNull() {
        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context(), null, null, null, null);

        assertNull(message.routing());
        assertNull(message.hit());
    }

    @Test
    void nodeIdComesFromMsgConstant() {
        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context(), null, null, null, null);

        assertEquals(Msg.NODE_ID, message.nodeId(),
                "节点标识取 Msg 常量（主机名自动探测，无配置项）");
        assertTrue(!message.nodeId().isBlank());
    }

    @Test
    void successCarriesUsageAndHitModel() {
        GatewayRequestContext context = context();
        context.putAttribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE,
                target("gpt-4o-mini", "upstream-gpt-4o-mini", 2, false));

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, usage(), 812L, null);

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

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, null, error);

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

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, null, error);

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

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, null,
                new RedisUnavailableException("traffic", "incr", "k:1", null));

        assertEquals("gateway_error", message.status());
        assertEquals(503, message.httpStatus());
        assertEquals("storage_unavailable", message.error().type());
        assertEquals("storage", message.error().stage());
    }

    @Test
    void traceIsFlattenedIntoMessage() {
        GatewayRequestContext context = context();
        CallTraceContext trace = new CallTraceContext();
        CallTraceContext.AttemptDraft first = trace.newAttempt(target("gpt-4o", "upstream-gpt-4o", 1, true), null);
        first.nextAttempt(2);
        first.failed(new GatewayUpstreamException("boom", "UPSTREAM_ERROR", 500, null, null), true);
        CallTraceContext.AttemptDraft second = trace.newAttempt(target("gpt-4o-mini", "upstream-gpt-4o-mini", 2, false), null);
        second.nextAttempt(2);
        second.succeeded(null);
        context.putAttribute(CallTraceContext.ATTRIBUTE, trace);

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, 100L, null);

        assertEquals(2, message.attempts().size(), "attempt 明细内嵌在消息里，不再独立成表");
        assertEquals("gpt-4o", message.attempts().get(0).targetCode());
        assertEquals("upstream_failure", message.attempts().get(0).outcome());
        assertTrue(message.attempts().get(0).countedForEjection());
        assertEquals("success", message.attempts().get(1).outcome());
        assertEquals(1, message.failoverTotal(), "成功前失败过一个候选");
        assertEquals(0, message.ejectedSkipped());
    }

    @Test
    void failoverAttemptsCarryTheirOwnUpstreamIds() {
        // 每个 attempt 打的是不同上游、拿回不同 id：二连 failover 下两行各带各自的 id，
        // 挂 request 级会丢链（设计文档 §4.4）
        GatewayRequestContext context = context();
        CallTraceContext trace = new CallTraceContext();
        CallTraceContext.AttemptDraft first = trace.newAttempt(target("gpt-4o", "upstream-gpt-4o", 1, true), null);
        first.nextAttempt(2);
        first.failed(new GatewayUpstreamException(
                        "upstream responded with status 503", "UPSTREAM_ERROR", 503, "{}", null,
                        "req_failed_4o"),
                true);
        CallTraceContext.AttemptDraft second = trace.newAttempt(target("gpt-4o-mini", "upstream-gpt-4o-mini", 2, false), null);
        second.nextAttempt(2);
        second.succeeded("req_ok_mini");
        context.putAttribute(CallTraceContext.ATTRIBUTE, trace);

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, 100L, null);

        assertEquals("req_failed_4o", message.attempts().get(0).upstreamRequestId(),
                "失败 attempt 的供应商 id 是工单里最关键的检索键");
        assertEquals("req_ok_mini", message.attempts().get(1).upstreamRequestId());
    }

    @Test
    void attemptWithoutUpstreamCallHasNullIds() {
        // attempt_no=0：被频控拒绝，从未发起上游调用——没有响应头，两个 id 必须是 null
        GatewayRequestContext context = context();
        CallTraceContext trace = new CallTraceContext();
        CallTraceContext.AttemptDraft draft = trace.newAttempt(target("gpt-4o-mini", "upstream-gpt-4o-mini", 1, true), null);
        draft.rejected("RATE_LIMIT_TPM");
        context.putAttribute(CallTraceContext.ATTRIBUTE, trace);

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, null, null);

        assertEquals(0, message.attempts().get(0).attempts());
        assertNull(message.attempts().get(0).upstreamRequestId());
    }

    @Test
    void serializedJsonCarriesUpstreamRequestId() {
        GatewayRequestContext context = context();
        CallTraceContext trace = new CallTraceContext();
        CallTraceContext.AttemptDraft draft = trace.newAttempt(target("gpt-4o-mini", "upstream-gpt-4o-mini", 1, true), null);
        draft.nextAttempt(2);
        draft.succeeded("req_abc123");
        context.putAttribute(CallTraceContext.ATTRIBUTE, trace);

        String json = JsonKit.toJson(GatewayTrafficMsgDto.of(context, null, null, 1L, null));

        assertTrue(json.contains("\"upstream_request_id\":\"req_abc123\""),
                "供应商 id 必须落进序列化消息（snake_case 由 JsonNaming 转换）: " + json);
    }

    @Test
    void serializedJsonOmitsNullUpstreamRequestId() {
        GatewayRequestContext context = context();
        CallTraceContext trace = new CallTraceContext();
        CallTraceContext.AttemptDraft draft = trace.newAttempt(target("gpt-4o-mini", "upstream-gpt-4o-mini", 1, true), null);
        draft.rejected("RATE_LIMIT_TPM");
        context.putAttribute(CallTraceContext.ATTRIBUTE, trace);

        String json = JsonKit.toJson(GatewayTrafficMsgDto.of(context, null, null, null, null));

        assertTrue(!json.contains("upstream_request_id"),
                "无数据的 id 不出现在消息里（NON_NULL）：下游按字段缺失识别，不是空串: " + json);
    }

    /**
     * 顶层 {@code request_id}（网关自生成）与 attempt 级 {@code upstream_request_id}
     * （上游返回）必须同时存在且互不相等——这两个 id 是双向对账的两端，混用即失效。
     */
    @Test
    void requestIdAndUpstreamRequestIdAreDistinctAndBothSerialized() {
        GatewayRequestContext context = context();
        CallTraceContext trace = new CallTraceContext();
        CallTraceContext.AttemptDraft draft = trace.newAttempt(target("gpt-4o-mini", "upstream-gpt-4o-mini", 1, true), null);
        draft.nextAttempt(2);
        draft.succeeded("req_upstream_side");
        context.putAttribute(CallTraceContext.ATTRIBUTE, trace);

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, 1L, null);
        String json = JsonKit.toJson(message);

        assertEquals(context.requestId(), message.requestId(), "顶层 request_id 取自请求上下文");
        assertTrue(message.requestId().matches("[0-9a-f]{32}"), "32 位小写 hex: " + message.requestId());
        assertTrue(json.contains("\"request_id\":\"" + context.requestId() + "\""),
                "我方编号必须落进序列化消息: " + json);
        assertTrue(json.contains("\"upstream_request_id\":\"req_upstream_side\""),
                "上游编号仍保留在 attempt 级: " + json);
        assertNotEquals(context.requestId(), "req_upstream_side");
    }

    @Test
    void serializedJsonKeepsCostAsString() {        GatewayRequestContext context = context();
        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, usage(), 1L, null);

        String json = JsonKit.toJson(message);

        assertTrue(json.contains("\"total_cost\":\"0.00150000\""), "成本必须是字符串形态: " + json);
        assertTrue(json.contains("\"cost\":\"0.00150000\""));
    }

    @Test
    void hitCarriesUpstreamUrl() {
        // 命中目标的上游完整请求地址随 HIT_TARGET_URL_ATTRIBUTE 落到 hit，排障能看出落点
        GatewayRequestContext context = context();
        context.putAttribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE,
                target("gpt-4o", "gpt-4o-2024-07-18", 1, true));
        context.putAttribute(GatewayRequestHandler.HIT_TARGET_URL_ATTRIBUTE,
                "https://openai.example/v1/chat/completions");
        context.routing(routing(target("gpt-4o", "gpt-4o-2024-07-18", 1, true)));

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, null, null);

        assertEquals("https://openai.example/v1/chat/completions", message.hit().upstreamUrl(),
                "命中目标带完整上游地址");
    }

    @Test
    void attemptsCarryUpstreamUrl() {
        // 每个 attempt 都带自己的上游地址：failover 排障能看出失败的是哪个端点
        GatewayRequestContext context = context();
        CallTraceContext trace = new CallTraceContext();
        CallTraceContext.AttemptDraft first = trace.newAttempt(target("gpt-4o", "upstream-gpt-4o", 1, true), null);
        first.nextAttempt(2);
        first.failed(new GatewayUpstreamException("boom", "UPSTREAM_ERROR", 500, null, null), true);
        first.setUpstreamUrl("https://openai.example/v1/chat/completions");
        CallTraceContext.AttemptDraft second = trace.newAttempt(target("gpt-4o-mini", "upstream-gpt-4o-mini", 2, false), null);
        second.nextAttempt(2);
        second.succeeded("req_ok_mini");
        second.setUpstreamUrl("https://openai.example/v1/chat/completions");
        context.putAttribute(CallTraceContext.ATTRIBUTE, trace);

        GatewayTrafficMsgDto message = GatewayTrafficMsgDto.of(context, null, null, 100L, null);

        assertEquals("https://openai.example/v1/chat/completions", message.attempts().get(0).upstreamUrl());
        assertEquals("https://openai.example/v1/chat/completions", message.attempts().get(1).upstreamUrl());
    }

    private static GatewayRequestContext context() {
        return new GatewayRequestContext("Bearer key", "rule-a", GatewayTraceContext.fromTraceparent(null), null, null);
    }

    private static org.chobit.knot.gateway.model.ResolvedRouting routing(RoutingRuleTargetDto... targets) {
        return new org.chobit.knot.gateway.model.ResolvedRouting(1L, "rule-a", 10L, "sk-routing-key", false,
                List.of(targets), null, null, null);
    }

    private static RoutingRuleTargetDto target(String code, String upstreamCode, int priority, boolean primary) {
        return new RoutingRuleTargetDto("MODEL", 1L, code, upstreamCode, code,
                "CHAT", "PA-1", priority, primary, null);
    }

    /**
     * 池内展开候选的形态：targetType 已被路由改写为 MODEL，targetCode 是池内模型 code，
     * poolCode 带来源池（{@code RoutingResolver.resolvePoolCandidates} 的行为）。
     */
    private static RoutingRuleTargetDto poolItem(String poolCode, String code, String upstreamCode,
                                                 int priority, boolean primary) {
        return new RoutingRuleTargetDto("MODEL", 1L, code, upstreamCode, code,
                "CHAT", "PA-1", priority, primary, poolCode);
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
