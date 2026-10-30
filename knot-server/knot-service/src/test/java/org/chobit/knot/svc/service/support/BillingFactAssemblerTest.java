package org.chobit.knot.svc.service.support;

import org.chobit.knot.svc.dto.routing.BillingCheckCode;
import org.chobit.knot.svc.dto.routing.BillingCheckStatus;
import org.chobit.knot.svc.dto.routing.BillingFact;
import org.chobit.knot.svc.dto.routing.BillingFactAssemblyInput;
import org.chobit.knot.svc.dto.routing.BillingFactUsage;
import org.chobit.knot.svc.dto.routing.BillingRuleSource;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.usage.calculator.TokenBillingModeCalculator;
import org.chobit.knot.adapter.usage.extractor.DefaultTokenUsageExtractor;
import org.chobit.knot.adapter.usage.extractor.UsageExtractorCatalog;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link BillingFactAssembler} 端到端：复用生产提取器 + 三级回退 + 归一化，产出可校验的 {@link BillingFact}。
 *
 * <p>不依赖 Spring 上下文：提取器 / 计算器 / 目录手工构造，mapper 用 Mockito 桩。
 * 同时验证「篡改被告账 → GATEWAY_RECOMPUTE_MATCH FAIL」的组装+校验联动。</p>
 */
class BillingFactAssemblerTest {

    private static final String RULE_CODE = "test-rule";

    private BillingRuleEntity rule() {
        BillingRuleEntity rule = new BillingRuleEntity();
        rule.setCode(RULE_CODE);
        rule.setBillingMode("TOKEN");
        rule.setUnit("1M_TOKENS");
        rule.setCurrency("USD");
        rule.setPricingPlan("FIXED");
        rule.setVersionCode("v1");
        rule.setConfigJson("{\"basePrices\":{\"input\":2,\"output\":4},\"cacheWriteMode\":\"standard\"}");
        return rule;
    }

    private BillingFactAssembler assembler(BillingRuleMapper mapper) {
        DefaultTokenUsageExtractor extractor = new DefaultTokenUsageExtractor(new TokenBillingModeCalculator());
        UsageExtractorCatalog catalog = new UsageExtractorCatalog(List.of(extractor));
        return new BillingFactAssembler(catalog, new BillingRuleFallbackResolver(mapper));
    }

    private BillingFactAssemblyInput input(Map<String, Object> body, String ruleCode, String extractorCode,
                                           NormalizedUsage gatewayBilling, Map<String, Object> upstreamUsage) {
        Map<String, Object> usage = body == null ? Map.of() : (Map<String, Object>) body.getOrDefault("usage", body);
        return new BillingFactAssemblyInput("m1", "fam1", ruleCode, extractorCode, "OPENAI", false,
                body, usage, Map.of(), gatewayBilling, upstreamUsage, Instant.now(), null);
    }

    private Map<String, Object> tokenBody(long input, long output, long total) {
        return Map.of("usage", Map.of(
                "prompt_tokens", input, "completion_tokens", output, "total_tokens", total));
    }

    @Test
    void shouldAssembleRecomputeMatchingProduction() {
        BillingRuleMapper mapper = mock(BillingRuleMapper.class);
        when(mapper.getActiveByRuleCode(eq(RULE_CODE), any(LocalDateTime.class))).thenReturn(rule());
        BillingFactAssembler assembler = assembler(mapper);

        BillingFact fact = assembler.assemble(input(tokenBody(1000, 500, 1500), RULE_CODE, "DEFAULT-TOKEN", null, null));

        // 影子重算账与网关生产同口径：input=1000 × 2/1e6 + output=500 × 4/1e6 = 0.004
        assertNotNull(fact.shadowAmount());
        assertEquals("0.004", fact.shadowAmount().totalCost());
        // TOKEN/FIXED 计算器产出 4 条明细（uncachedInput / cachedRead / cachedWrite / output），
        // 缓存两行为零量零额；非零额两行之和 = 总价
        assertEquals(4, fact.shadowAmount().details().size());
        BigDecimal sum = fact.shadowAmount().details().stream()
                .map(d -> new java.math.BigDecimal(d.cost()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        assertEquals(0, sum.compareTo(new java.math.BigDecimal("0.004")));
        // 用量视图
        BillingFactUsage usage = fact.usage();
        assertTrue(usage.tokenBased());
        assertEquals(1000L, usage.inputTokens());
        assertEquals(500L, usage.outputTokens());
        assertEquals(1500L, usage.totalTokens());
        // 规则来源 EXPLICIT
        assertEquals(BillingRuleSource.EXPLICIT, fact.ruleRef().matchedBy());
        assertEquals("TOKEN", fact.ruleRef().billingMode());
        // 正确配置不应被误标成回落或缺量
        assertFalse(fact.extractorFallback());
        assertFalse(fact.usageAbsent());
    }

    @Test
    void shouldUseDefaultExtractorWhenCodeAbsent() {
        BillingRuleMapper mapper = mock(BillingRuleMapper.class);
        when(mapper.getActiveByRuleCode(eq(RULE_CODE), any(LocalDateTime.class))).thenReturn(rule());
        BillingFactAssembler assembler = assembler(mapper);

        // extractorCode 为 null → 回落默认 DEFAULT-TOKEN，仍应正确重算
        BillingFact fact = assembler.assemble(input(tokenBody(1000, 500, 1500), RULE_CODE, null, null, null));
        assertEquals("0.004", fact.shadowAmount().totalCost());
        assertTrue(fact.extractorFallback(), "回落过默认提取器必须标记出来，否则编码缺失在影子链里不可见");
    }

    @Test
    void shouldFlagFallbackWhenExtractorCodeUnknown() {
        BillingRuleMapper mapper = mock(BillingRuleMapper.class);
        when(mapper.getActiveByRuleCode(eq(RULE_CODE), any(LocalDateTime.class))).thenReturn(rule());
        BillingFactAssembler assembler = assembler(mapper);

        BillingFact fact = assembler.assemble(
                input(tokenBody(1000, 500, 1500), RULE_CODE, "NOT-A-REAL-EXTRACTOR", null, null));

        assertTrue(fact.extractorFallback(), "未知 extractor 编码不再静默——影子链不抛异常，但必须标记");
        assertFalse(fact.usageAbsent(), "回落到默认提取器后仍能算出用量，不应同时标成用量缺失");
    }

    @Test
    void shouldFlagAbsentUsageWhenUpstreamReportsNothing() {
        BillingRuleMapper mapper = mock(BillingRuleMapper.class);
        when(mapper.getActiveByRuleCode(eq(RULE_CODE), any(LocalDateTime.class))).thenReturn(rule());
        BillingFactAssembler assembler = assembler(mapper);

        BillingFact fact = assembler.assemble(
                input(Map.of("id", "1"), RULE_CODE, "DEFAULT-TOKEN", null, null));

        assertTrue(fact.usageAbsent(), "提取为空必须标记——影子账不可信要能被裁判层看见");
        assertNull(fact.shadowAmount(), "无用量不生成影子账");
        assertFalse(fact.extractorFallback(), "提取器本身是可解析的，不应误标回落");
    }

    @Test
    void shouldFlagMissingRuleAsNoShadow() {
        BillingRuleMapper mapper = mock(BillingRuleMapper.class);
        when(mapper.getActiveByRuleCode(eq(RULE_CODE), any(LocalDateTime.class))).thenReturn(null);
        when(mapper.getActiveByModelFamily(any(), any(LocalDateTime.class))).thenReturn(null);
        BillingFactAssembler assembler = assembler(mapper);

        BillingFact fact = assembler.assemble(input(tokenBody(1000, 500, 1500), RULE_CODE, "DEFAULT-TOKEN", null, null));
        assertEquals(BillingRuleSource.MISSING, fact.ruleRef().matchedBy());
        assertNull(fact.shadowAmount());
    }

    @Test
    void shouldExtractUpstreamCost() {
        BillingRuleMapper mapper = mock(BillingRuleMapper.class);
        when(mapper.getActiveByRuleCode(eq(RULE_CODE), any(LocalDateTime.class))).thenReturn(rule());
        BillingFactAssembler assembler = assembler(mapper);

        BillingFact fact = assembler.assemble(input(tokenBody(1000, 500, 1500), RULE_CODE, "DEFAULT-TOKEN", null,
                Map.of("cost", "0.0039")));
        assertEquals(0, new BigDecimal("0.0039").compareTo(fact.upstreamCost()));
    }

    @Test
    void shouldFailGatewayRecomputeMatchOnTamperedDefendant() {
        BillingRuleMapper mapper = mock(BillingRuleMapper.class);
        when(mapper.getActiveByRuleCode(eq(RULE_CODE), any(LocalDateTime.class))).thenReturn(rule());
        BillingFactAssembler assembler = assembler(mapper);

        // 被告账被篡改（总价翻倍），而影子重算账仍是 0.004
        NormalizedUsage tampered = new NormalizedUsage(1500L, "0.008", "USD", "v1", List.of(
                new NormalizedUsageDetail("uncachedInput", 1000L, "0.004", "2"),
                new NormalizedUsageDetail("output", 500L, "0.004", "4")));
        BillingFact fact = assembler.assemble(input(tokenBody(1000, 500, 1500), RULE_CODE, "DEFAULT-TOKEN", tampered, null));

        assertEquals(BillingCheckStatus.FAIL, BillingFactChecks.runAll(fact).stream()
                .filter(r -> r.code() == BillingCheckCode.GATEWAY_RECOMPUTE_MATCH).findFirst().orElseThrow().status());
    }
}
