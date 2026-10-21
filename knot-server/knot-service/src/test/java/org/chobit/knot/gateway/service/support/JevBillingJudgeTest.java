package org.chobit.knot.gateway.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.config.JevJudgeProperties;
import org.chobit.knot.gateway.dto.routing.BillingFactAmount;
import org.chobit.knot.gateway.dto.routing.BillingFactAmountLine;
import org.chobit.knot.gateway.dto.routing.BillingFactRuleRef;
import org.chobit.knot.gateway.dto.routing.BillingFactSubject;
import org.chobit.knot.gateway.dto.routing.BillingRuleSource;
import org.chobit.knot.gateway.dto.routing.JevJudgeContext;
import org.chobit.knot.gateway.entity.RoutingConsumerEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.chobit.knot.gateway.mapper.RoutingConsumerMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleMapper;
import org.chobit.knot.gateway.vo.routing.JevAnswerItem;
import org.chobit.knot.gateway.vo.routing.RoutingBillingVerdict;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JevBillingJudge} 单测（v7 §10 门禁：契约 / 解析 / 降级 / state 断言 / secretKey 不落日志）。
 *
 * <p>用 {@link FakeDecisionsTransport} 替代真实 RestClient，断言「凭据仅进 Authorization 头、
 * 不进请求体、不落日志」以及错误码降级与 413 截断重试。state 内容断言覆盖双盲硬约束
 * （§4.2：不含影子重算 / 解析用量视图 / 自检结论）。</p>
 */
class JevBillingJudgeTest {

    private static final String GOOD_JSON = """
            {
              "answers": {
                "usage_parse_ok": {"choice": "CORRECT", "confidence": 0.96},
                "rule_match_ok": {"choice": "APPROPRIATE", "confidence": 0.92, "probabilities": {"APPROPRIATE": 0.92, "FALLBACK_ONLY": 0.05}},
                "cost_plausible": {"choice": "PLAUSIBLE", "confidence": 0.91},
                "risk_level": {"choice": "LOW", "confidence": 0.90}
              }
            }
            """;

    private static final String PARTIAL_JSON = """
            {
              "answers": {
                "usage_parse_ok": {"choice": "CORRECT"},
                "rule_match_ok": {"choice": "FALLBACK_ONLY"},
                "cost_plausible": {"choice": "SUSPICIOUS"}
              }
            }
            """;

    private static final String SECRET = "DO_NOT_LOG_SECRET_123";

    @Test
    void unavailableWhenRuleCodeMissing() {
        JevJudgeProperties props = new JevJudgeProperties(); // ruleCode / consumerCode 均为 null
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(2), "http://gw", "sid");

        assertFalse(v.isAvailable());
        assertTrue(v.getReason().contains("judge-rule-code"), v.getReason());
        assertEquals(0, transport.calls, "配置缺失不应发起调用");
    }

    @Test
    void unavailableWhenConsumerMissing() {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(null);
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(enabledRule());
        FakeDecisionsTransport transport = new FakeDecisionsTransport();

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(2), "http://gw", "sid");

        assertFalse(v.isAvailable());
        assertTrue(v.getReason().contains("消费者不存在"), v.getReason());
        assertEquals(0, transport.calls);
    }

    @Test
    void unavailableWhenRuleMissing() {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(null);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(2), "http://gw", "sid");

        assertFalse(v.isAvailable());
        assertTrue(v.getReason().contains("规则不存在"), v.getReason());
    }

    @Test
    void unavailableWhenRuleDisabled() {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = new RoutingRuleEntity();
        rule.setStatus(0); // 停用
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(2), "http://gw", "sid");

        assertFalse(v.isAvailable());
        assertTrue(v.getReason().contains("未启用"), v.getReason());
    }

    @Test
    void availableWhenJudgeSucceeds() {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = enabledRule();
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport(); // 空脚本 → GOOD_JSON

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(2), "http://gw", "sid");

        assertTrue(v.isAvailable());
        assertNull(v.getReason());
        assertNotNull(v.getSummary());
        assertEquals(4, v.getAnswers().size());
        assertEquals("CORRECT", find(v, "usage_parse_ok").getValue());
        assertEquals("归一化完整且与上游依据一致", find(v, "usage_parse_ok").getNote());
        assertEquals(0.96, find(v, "usage_parse_ok").getConfidence(), 0.0001);
        assertEquals("PLAUSIBLE", find(v, "cost_plausible").getValue());
        assertEquals("LOW", find(v, "risk_level").getValue());
        assertEquals("没有明显异常证据", find(v, "risk_level").getNote());
        assertTrue(v.getSummary().contains("风险: 没有明显异常证据"));
        // 凭据仅进 Authorization 头
        assertEquals("Bearer " + SECRET, transport.lastAuthorization);
    }

    @Test
    void parsesExplicitInsufficientEvidenceChoices() {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(enabledRule());
        FakeDecisionsTransport transport = new FakeDecisionsTransport();
        transport.script("""
                {"answers":{
                  "usage_parse_ok":{"choice":"INSUFFICIENT_EVIDENCE"},
                  "cost_plausible":{"choice":"INSUFFICIENT_EVIDENCE"},
                  "risk_level":{"choice":"MEDIUM"}
                }}
                """);

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict verdict = judge.judge(sampleContext(2), "http://gw", "sid");

        assertEquals("ANSWERED", find(verdict, "usage_parse_ok").getStatus());
        assertEquals("INSUFFICIENT_EVIDENCE", find(verdict, "usage_parse_ok").getValue());
        assertEquals("必要的上游原始 usage 字段缺失，无法核验", find(verdict, "usage_parse_ok").getNote());
        assertEquals("ANSWERED", find(verdict, "cost_plausible").getStatus());
        assertEquals("必要账单或用量依据缺失，无法判断", find(verdict, "cost_plausible").getNote());
        assertEquals("MEDIUM", find(verdict, "risk_level").getValue());
    }

    @Test
    void missingAnswerMarkedNotReturned() {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = enabledRule();
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();
        transport.script(PARTIAL_JSON);

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(2), "http://gw", "sid");

        assertTrue(v.isAvailable());
        JevAnswerItem risk = find(v, "risk_level");
        assertEquals("NOT_RETURNED", risk.getStatus());
        JevAnswerItem ruleMatch = find(v, "rule_match_ok");
        assertEquals("ANSWERED", ruleMatch.getStatus());
        assertEquals("FALLBACK_ONLY", ruleMatch.getValue());
        assertEquals("只命中默认/兜底规则", ruleMatch.getNote());
        JevAnswerItem cost = find(v, "cost_plausible");
        assertEquals("SUSPICIOUS", cost.getValue());
        assertEquals("存在可证实的金额量级、币种或规则矛盾", cost.getNote());
    }

    @Test
    void degradeOn402() {
        assertDegrade(402, "余额不足");
    }

    @Test
    void degradeOn413AfterRetry() {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = enabledRule();
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();
        transport.script(413, 413); // 两次都 413

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(2), "http://gw", "sid");

        assertFalse(v.isAvailable());
        assertTrue(v.getReason().contains("413"), v.getReason());
        assertEquals(2, transport.calls);
    }

    @Test
    void succeedAfter413Truncate() {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = enabledRule();
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();
        transport.script(413, GOOD_JSON); // 首次 413，截断重试成功

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(25), "http://gw", "sid");

        assertTrue(v.isAvailable());
        assertEquals(2, transport.calls);
    }

    @Test
    void degradeOn429AfterRetry() {
        assertDegrade(429, "429");
    }

    @Test
    void degradeOn5xx() {
        assertDegrade(503, "网关错误");
    }

    /**
     * 网关把所有上游非 2xx 统一回 502，但错误体携带 {@code error.upstreamStatus}。
     * 裁判必须据此还原语义——否则 402/413 分支在本架构下不可达，管理员只能看到「网关错误 502」，
     * 分不清「余额不足」与「真故障」（Batch4 冒烟场景 7 抓到的缺陷）。
     */
    @Test
    void recoversUpstream402SemanticsFromGateway502() {
        assertDegradeWithBody(502, gatewayErrorBody(402, "Insufficient credits"), "余额不足");
    }

    @Test
    void recoversUpstream429SemanticsFromGateway502() {
        // 429 属可重试错误：透出后仍应走一次退避重试，失败才降级
        assertDegradeWithBodyRepeated(502, gatewayErrorBody(429, "Rate limit exceeded"), "限流", 2);
    }

    @Test
    void gateway502WithUpstream413TriggersTruncateRetry() {
        // 413 必须触发「丢弃明细」的截断重试，否则透出场景下永远救不回来
        assertDegradeWithBodyRepeated(502, gatewayErrorBody(413, "payload too large"), "413", 2);
    }

    @Test
    void gateway502WithUpstream413SucceedsAfterTruncate() throws Exception {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = enabledRule();
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();
        transport.script(new Fail(502, gatewayErrorBody(413, "payload too large")), GOOD_JSON);

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(25), "http://gw", "sid");

        assertTrue(v.isAvailable(), "截断重试后应拿到结论");
        assertEquals(2, transport.calls);
        // 重试体必须是 minimal：billing.details 清空，只留汇总
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) transport.lastBody.get("state");
        @SuppressWarnings("unchecked")
        Map<String, Object> produced = (Map<String, Object>) state.get("produced");
        @SuppressWarnings("unchecked")
        Map<String, Object> billing = (Map<String, Object>) produced.get("billing");
        @SuppressWarnings("unchecked")
        List<Object> details = (List<Object>) billing.get("details");
        assertTrue(details.isEmpty(), "413 重试必须丢弃明细");
    }

    @Test
    void gateway502WithoutUpstreamStatusStillReportedAsGatewayError() {
        assertDegradeWithBody(502, "{\"error\":{\"message\":\"boom\",\"type\":\"upstream_error\"}}", "网关错误");
    }

    private static String gatewayErrorBody(int upstreamStatus, String upstreamMessage) {
        return "{\"error\":{\"message\":\"upstream failed\",\"type\":\"upstream_error\","
                + "\"code\":\"UPSTREAM_ERROR\",\"upstreamStatus\":" + upstreamStatus
                + ",\"upstreamMessage\":\"" + upstreamMessage + "\"}}";
    }

    private void assertDegradeWithBody(int status, String body, String expectFragment) {
        assertDegradeWithBodyRepeated(status, body, expectFragment, 1);
    }

    /** 同一错误脚本重复 {@code expectedCalls} 次（可重试错误会被重试一次），断言降级原因与调用次数 */
    private void assertDegradeWithBodyRepeated(int status, String body, String expectFragment, int expectedCalls) {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = enabledRule();
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();
        Object[] script = new Object[expectedCalls];
        for (int i = 0; i < expectedCalls; i++) {
            script[i] = new Fail(status, body);
        }
        transport.script(script);

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(2), "http://gw", "sid");

        assertFalse(v.isAvailable());
        assertTrue(v.getReason().contains(expectFragment), v.getReason());
        assertEquals(expectedCalls, transport.calls);
    }

    @Test
    void stateContentHonorsDoubleBlindAndTruncation() throws Exception {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = enabledRule();
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();
        // 25 条明细，验证 details 截断到 20
        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        judge.judge(sampleContext(25), "http://gw", "sid");

        String json = new ObjectMapper().writeValueAsString(transport.lastBody);
        String lower = json.toLowerCase();
        // 双盲硬约束：state 绝不出现影子重算 / 解析用量视图 / 自检结论
        assertFalse(lower.contains("shadow"), "state 不应含 shadow");
        assertFalse(lower.contains("computed"), "state 不应含 computed");
        assertFalse(lower.contains("parsedusage"), "state 不应含 parsedUsage");
        assertFalse(lower.contains("gateway_recompute_match"), "state 不应含自检结论");

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) transport.lastBody.get("state");
        @SuppressWarnings("unchecked")
        Map<String, Object> produced = (Map<String, Object>) state.get("produced");
        @SuppressWarnings("unchecked")
        Map<String, Object> billing = (Map<String, Object>) produced.get("billing");
        @SuppressWarnings("unchecked")
        List<Object> details = (List<Object>) billing.get("details");
        assertTrue(details.size() <= 20, "details 应截断到 <=20，实际 " + details.size());

        // upstreamUsage 仅数值键
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) state.get("evidence");
        @SuppressWarnings("unchecked")
        Map<String, Object> upU = (Map<String, Object>) evidence.get("upstreamUsage");
        assertFalse(upU.containsKey("model"), "非数值键应被过滤");
        assertFalse(upU.containsKey("id"), "非数值键应被过滤");
        assertTrue(upU.containsKey("prompt_tokens"));
        @SuppressWarnings("unchecked")
        Map<String, Object> promptDetails = (Map<String, Object>) upU.get("prompt_tokens_details");
        assertEquals(0, promptDetails.get("cached_tokens"));
        @SuppressWarnings("unchecked")
        Map<String, Object> completionDetails = (Map<String, Object>) upU.get("completion_tokens_details");
        assertEquals(254, completionDetails.get("reasoning_tokens"));
        // requestAttributes 不含敏感键
        @SuppressWarnings("unchecked")
        Map<String, Object> reqAttr = (Map<String, Object>) evidence.get("requestAttributes");
        assertFalse(reqAttr.containsKey("messages"), "prompt 等敏感键不得进 state");
        assertTrue(reqAttr.containsKey("size"));

        // 契约合法：四题均使用明确 choice；风险等级和不确定态均可直接表达
        @SuppressWarnings("unchecked")
        Map<String, Object> questions = (Map<String, Object>) transport.lastBody.get("questions");
        assertEquals(4, questions.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> risk = (Map<String, Object>) questions.get("risk_level");
        assertEquals("choice", risk.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, String> riskCriteria = (Map<String, String>) risk.get("criteria");
        assertEquals(Set.of("LOW", "MEDIUM", "HIGH"), riskCriteria.keySet());
        @SuppressWarnings("unchecked")
        Map<String, Object> usage = (Map<String, Object>) questions.get("usage_parse_ok");
        assertEquals("choice", usage.get("type"));
        assertTrue(((String) usage.get("instructions")).contains("INSUFFICIENT_EVIDENCE"));
        assertTrue(((String) usage.get("instructions")).contains("reasoning_tokens 通常是 completion_tokens 的子集"));
        assertTrue(((String) usage.get("instructions")).contains("completion_tokens 对应 output.tokens.text + output.tokens.reasoning"));
        @SuppressWarnings("unchecked")
        Map<String, Object> cost = (Map<String, Object>) questions.get("cost_plausible");
        assertEquals("choice", cost.get("type"));
        assertTrue(((String) cost.get("instructions")).contains("upstreamCost 缺失本身不代表证据不足"));
        @SuppressWarnings("unchecked")
        Map<String, Object> provider = (Map<String, Object>) transport.lastBody.get("provider");
        assertEquals("deny", provider.get("data_collection"));
        // 上游 provider 白名单与 fallback 开关已移除，防止被误加回
        assertFalse(provider.containsKey("allow_fallbacks"));
        assertFalse(provider.containsKey("only"));
    }

    @Test
    void secretKeyNeverLoggedOrInBody() {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = enabledRule();
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();

        Logger logger = (Logger) LoggerFactory.getLogger(JevBillingJudge.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
            judge.judge(sampleContext(2), "http://gw", "sid");
        } finally {
            logger.detachAppender(appender);
        }

        // 凭据仅进 Authorization 头
        assertEquals("Bearer " + SECRET, transport.lastAuthorization);
        // 请求体不含凭据
        assertFalse(transport.lastBody.toString().contains(SECRET), "请求体不得含 secretKey");
        // 日志不含凭据
        boolean leaked = appender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains(SECRET));
        assertFalse(leaked, "日志不得含 secretKey");
    }

    // ---- 工具 ----

    private void assertDegrade(int status, String expectFragment) {
        JevJudgeProperties props = configuredProps();
        RoutingConsumerMapper cMock = mock(RoutingConsumerMapper.class);
        RoutingRuleMapper rMock = mock(RoutingRuleMapper.class);
        when(cMock.getByCode("jev-judge-client")).thenReturn(enabledConsumer());
        RoutingRuleEntity rule = enabledRule();
        when(rMock.getByRuleCode("jev-billing-judge")).thenReturn(rule);
        FakeDecisionsTransport transport = new FakeDecisionsTransport();
        transport.script(status, status); // 两次同码（402/429：429 重试一次；402 仅一次）

        JevBillingJudge judge = new JevBillingJudge(props, cMock, rMock, transport, new ObjectMapper());
        RoutingBillingVerdict v = judge.judge(sampleContext(2), "http://gw", "sid");

        assertFalse(v.isAvailable());
        assertTrue(v.getReason().contains(expectFragment), v.getReason());
    }

    private JevJudgeProperties configuredProps() {
        JevJudgeProperties props = new JevJudgeProperties();
        props.setJudgeRuleCode("jev-billing-judge");
        return props;
    }

    private RoutingRuleEntity enabledRule() {
        RoutingRuleEntity rule = new RoutingRuleEntity();
        rule.setStatus(1);
        rule.setConsumerCode("jev-judge-client");
        return rule;
    }

    private RoutingConsumerEntity enabledConsumer() {
        RoutingConsumerEntity consumer = new RoutingConsumerEntity();
        consumer.setSecretKey(SECRET);
        consumer.setStatus(1);
        return consumer;
    }

    private JevAnswerItem find(RoutingBillingVerdict v, String key) {
        return v.getAnswers().stream()
                .filter(a -> a.getKey().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("答案缺失: " + key));
    }

    private JevJudgeContext sampleContext(int detailCount) {
        BillingFactSubject subject = new BillingFactSubject(
                "gpt-4o", "OPENAI_CHAT", false, BillingRuleSource.EXPLICIT);
        Map<String, Object> gatewayUsage = Map.of(
                "prompt_tokens", 1000, "completion_tokens", 500, "total_tokens", 1500);
        List<BillingFactAmountLine> details = new ArrayList<>();
        for (int i = 0; i < detailCount; i++) {
            details.add(new BillingFactAmountLine("LINE_" + i, (long) i, "0.001", "1.0"));
        }
        BillingFactAmount billing = new BillingFactAmount(1500L, "0.004", "USD", details);
        Map<String, Object> upstreamUsage = new LinkedHashMap<>();
        upstreamUsage.put("prompt_tokens", 1000);
        upstreamUsage.put("completion_tokens", 500);
        upstreamUsage.put("total_tokens", 1500);
        upstreamUsage.put("prompt_tokens_details", Map.of("cached_tokens", 0));
        upstreamUsage.put("completion_tokens_details", Map.of("reasoning_tokens", 254));
        upstreamUsage.put("model", "gpt-4o"); // 非数值键，应被过滤
        upstreamUsage.put("id", "chatcmpl-x"); // 非数值键，应被过滤
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("size", "1024x1024");
        requestBody.put("messages", List.of("system", "hello")); // 敏感键，应被过滤
        BillingFactRuleRef ruleRef = new BillingFactRuleRef(
                "r1", "TOKEN", "1M_TOKENS", "USD", "FIXED", BillingRuleSource.EXPLICIT, "v1");
        return new JevJudgeContext(subject, gatewayUsage, billing, upstreamUsage,
                new BigDecimal("0.004"), ruleRef, requestBody);
    }

    /** 脚本化假传输：队首为 String=成功响应体，Integer=抛对应状态码；空则回 GOOD_JSON */
    static final class FakeDecisionsTransport implements DecisionsTransport {
        String lastAuthorization;
        Map<String, Object> lastBody;
        int calls = 0;
        private final Deque<Object> script = new ArrayDeque<>();

        void script(Object... items) {
            for (Object item : items) {
                script.addLast(item);
            }
        }

        @Override
        public String post(String gatewayBaseUrl, String bearerToken, String ruleCode, Map<String, Object> body) {
            lastAuthorization = "Bearer " + (bearerToken == null ? "" : bearerToken.trim());
            lastBody = body;
            calls++;
            Object next = script.pollFirst();
            if (next instanceof String s) {
                return s;
            }
            if (next instanceof Fail f) {
                byte[] payload = f.body() == null ? new byte[0] : f.body().getBytes(StandardCharsets.UTF_8);
                throw HttpClientErrorException.create(
                        HttpStatus.valueOf(f.status()), "err", null, payload, StandardCharsets.UTF_8);
            }
            if (next instanceof Integer code) {
                throw HttpClientErrorException.create(
                        HttpStatus.valueOf(code), "err", null, new byte[0], null);
            }
            return GOOD_JSON;
        }
    }

    /** 带响应体的失败脚本项：用于模拟网关 502 且错误体含 upstreamStatus */
    record Fail(int status, String body) {
    }
}
