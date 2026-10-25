package org.chobit.knot.svc.service.support;

import org.chobit.knot.svc.dto.routing.BillingCheckCode;
import org.chobit.knot.svc.dto.routing.BillingCheckResult;
import org.chobit.knot.svc.dto.routing.BillingCheckStatus;
import org.chobit.knot.svc.dto.routing.BillingFact;
import org.chobit.knot.svc.dto.routing.BillingFactAmount;
import org.chobit.knot.svc.dto.routing.BillingFactSubject;
import org.chobit.knot.svc.dto.routing.BillingFactAmountLine;
import org.chobit.knot.svc.dto.routing.BillingFactRuleRef;
import org.chobit.knot.svc.dto.routing.BillingFactUsage;
import org.chobit.knot.svc.dto.routing.BillingRuleSource;
import org.chobit.knot.svc.service.support.BillingFactChecks;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计费自检七项逐态覆盖：每项 PASS / FAIL / SKIP 明确，含篡改金额反向用例与 cost 缺失 SKIP。
 *
 * <p>纯函数测试：直接构造 {@link BillingFact}，不依赖 Spring / 数据库。</p>
 */
class BillingFactChecksTest {

    // 标准 TOKEN 场景：input=1000 / output=500 / total=1500，单价 2 / 4，1M 单位
    private static final BillingFactUsage STANDARD_USAGE = new BillingFactUsage(true, 1000L, 500L, 1500L, 0L, 0L, 0L);
    private static final BillingFactUsage ZERO_USAGE = new BillingFactUsage(true, 0L, 0L, 0L, 0L, 0L, 0L);
    private static final BillingFactUsage BAD_TOTAL_USAGE = new BillingFactUsage(true, 1000L, 500L, 999L, 0L, 0L, 0L);
    private static final BillingFactUsage MEDIA_USAGE = new BillingFactUsage(false, 0L, 0L, 0L, 0L, 0L, 2L);

    private static final BillingFactAmount STANDARD_AMOUNT = new BillingFactAmount(1500L, "0.004", "USD", List.of(
            new BillingFactAmountLine("uncachedInput", 1000L, "0.002", "2"),
            new BillingFactAmountLine("output", 500L, "0.002", "4")));

    // 篡改被告账：总价翻倍（0.008），明细同步翻倍
    private static final BillingFactAmount TAMPERED_AMOUNT = new BillingFactAmount(1500L, "0.008", "USD", List.of(
            new BillingFactAmountLine("uncachedInput", 1000L, "0.004", "2"),
            new BillingFactAmountLine("output", 500L, "0.004", "4")));

    // 明细公式不符：uncachedInput cost=0.003（期望 0.002）
    private static final BillingFactAmount BAD_FORMULA_AMOUNT = new BillingFactAmount(1500L, "0.005", "USD", List.of(
            new BillingFactAmountLine("uncachedInput", 1000L, "0.003", "2"),
            new BillingFactAmountLine("output", 500L, "0.002", "4")));

    private static BillingFact standard(BillingFactUsage usage, BillingFactAmount shadow, BillingFactAmount gateway,
                                        BigDecimal upstream, BillingRuleSource source) {
        BillingFactRuleRef ruleRef = new BillingFactRuleRef("r1", "TOKEN", "1M_TOKENS", "USD", "FIXED", source, "v1");
        return new BillingFact(new BillingFactSubject("m1", "OPENAI", false, source),
                usage, ruleRef, shadow, gateway, upstream);
    }

    private static BillingCheckResult result(BillingFact fact, BillingCheckCode code) {
        return BillingFactChecks.runAll(fact).stream()
                .filter(r -> r.code() == code).findFirst().orElseThrow();
    }

    @Test
    void shouldPassAllOnStandardTokenScenario() {
        BillingFact fact = standard(STANDARD_USAGE, STANDARD_AMOUNT, STANDARD_AMOUNT, new BigDecimal("0.0039"), BillingRuleSource.EXPLICIT);
        for (BillingCheckCode code : BillingCheckCode.values()) {
            assertEquals(BillingCheckStatus.PASS, result(fact, code).status(),
                    "应全 PASS，但 " + code + " 为 " + result(fact, code));
        }
    }

    @Test
    void usageNotEmptyShouldFailOnZeroUsage() {
        BillingFact fact = standard(ZERO_USAGE, STANDARD_AMOUNT, STANDARD_AMOUNT, null, BillingRuleSource.EXPLICIT);
        assertEquals(BillingCheckStatus.FAIL, result(fact, BillingCheckCode.USAGE_NOT_EMPTY).status());
    }

    @Test
    void totalTokensConsistentShouldFailOnMismatch() {
        BillingFact fact = standard(BAD_TOTAL_USAGE, STANDARD_AMOUNT, STANDARD_AMOUNT, null, BillingRuleSource.EXPLICIT);
        BillingCheckResult r = result(fact, BillingCheckCode.TOTAL_TOKENS_CONSISTENT);
        assertEquals(BillingCheckStatus.FAIL, r.status());
        assertTrue(r.message().contains("999"));
    }

    @Test
    void costFormulaMatchShouldFailOnInconsistentLine() {
        BillingFact fact = standard(STANDARD_USAGE, BAD_FORMULA_AMOUNT, BAD_FORMULA_AMOUNT, null, BillingRuleSource.EXPLICIT);
        assertEquals(BillingCheckStatus.FAIL, result(fact, BillingCheckCode.COST_FORMULA_MATCH).status());
    }

    @Test
    void gatewayRecomputeMatchShouldFailOnTamperedDefendant() {
        // 关键反向用例：篡改被告账（0.008）vs 重算账（0.004）→ FAIL，且其余项不受影响
        BillingFact fact = standard(STANDARD_USAGE, STANDARD_AMOUNT, TAMPERED_AMOUNT, null, BillingRuleSource.EXPLICIT);
        assertEquals(BillingCheckStatus.FAIL, result(fact, BillingCheckCode.GATEWAY_RECOMPUTE_MATCH).status());
        // 其余项仍 PASS（不误伤）
        assertEquals(BillingCheckStatus.PASS, result(fact, BillingCheckCode.USAGE_NOT_EMPTY).status());
        assertEquals(BillingCheckStatus.PASS, result(fact, BillingCheckCode.TOTAL_TOKENS_CONSISTENT).status());
        assertEquals(BillingCheckStatus.PASS, result(fact, BillingCheckCode.COST_FORMULA_MATCH).status());
        assertEquals(BillingCheckStatus.PASS, result(fact, BillingCheckCode.RULE_MODE_MATCH).status());
        assertEquals(BillingCheckStatus.PASS, result(fact, BillingCheckCode.RULE_SOURCE_EXPECTED).status());
        assertEquals(BillingCheckStatus.SKIP, result(fact, BillingCheckCode.UPSTREAM_COST_DEVIATION).status());
    }

    @Test
    void gatewayRecomputeMatchShouldSkipWhenDefendantAbsent() {
        BillingFact fact = standard(STANDARD_USAGE, STANDARD_AMOUNT, null, null, BillingRuleSource.EXPLICIT);
        assertEquals(BillingCheckStatus.SKIP, result(fact, BillingCheckCode.GATEWAY_RECOMPUTE_MATCH).status());
    }

    @Test
    void ruleModeMatchShouldFailOnMismatch() {
        // TOKEN 规则但非 token 用量（媒体形态）
        BillingFactRuleRef mediaRuleRef = new BillingFactRuleRef("r1", "TOKEN", "1M_TOKENS", "USD", "FIXED",
                BillingRuleSource.EXPLICIT, "v1");
        BillingFact fact = new BillingFact(new BillingFactSubject("m1", "OPENAI", false, BillingRuleSource.EXPLICIT),
                MEDIA_USAGE, mediaRuleRef, STANDARD_AMOUNT, STANDARD_AMOUNT, null);
        assertEquals(BillingCheckStatus.FAIL, result(fact, BillingCheckCode.RULE_MODE_MATCH).status());
    }

    @Test
    void ruleSourceExpectedShouldFailOnFallback() {
        BillingFact fact = standard(STANDARD_USAGE, STANDARD_AMOUNT, STANDARD_AMOUNT, null, BillingRuleSource.FAMILY);
        assertEquals(BillingCheckStatus.FAIL, result(fact, BillingCheckCode.RULE_SOURCE_EXPECTED).status());
        assertTrue(result(fact, BillingCheckCode.RULE_SOURCE_EXPECTED).message().contains("FAMILY"));
    }

    @Test
    void upstreamCostDeviationShouldSkipWhenAbsent() {
        BillingFact fact = standard(STANDARD_USAGE, STANDARD_AMOUNT, STANDARD_AMOUNT, null, BillingRuleSource.EXPLICIT);
        assertEquals(BillingCheckStatus.SKIP, result(fact, BillingCheckCode.UPSTREAM_COST_DEVIATION).status());
    }

    @Test
    void upstreamCostDeviationShouldFailOnTwoOrdersOfMagnitude() {
        // 上游 cost 远大于 knot 费用（100 vs 0.004 → ratio 25000）
        BillingFact fact = standard(STANDARD_USAGE, STANDARD_AMOUNT, STANDARD_AMOUNT, new BigDecimal("100"), BillingRuleSource.EXPLICIT);
        assertEquals(BillingCheckStatus.FAIL, result(fact, BillingCheckCode.UPSTREAM_COST_DEVIATION).status());
    }

    @Test
    void upstreamCostDeviationShouldPassWithinMagnitude() {
        BillingFact fact = standard(STANDARD_USAGE, STANDARD_AMOUNT, STANDARD_AMOUNT, new BigDecimal("0.0039"), BillingRuleSource.EXPLICIT);
        assertEquals(BillingCheckStatus.PASS, result(fact, BillingCheckCode.UPSTREAM_COST_DEVIATION).status());
    }
}
