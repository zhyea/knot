package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayTrafficGuardTest {

    private static final String MODEL = "MODEL";
    private static final long MODEL_ID = 7L;
    private static final String PROVIDER = "PROVIDER";
    private static final long PROVIDER_ID = 99L;
    private static final String PROVIDER_CODE = "account-code";

    private GatewayTrafficGuard guard;

    @Test
    void allowsWhenNoPolicyConfigured() {
        setUpWith(new TrafficPolicies(null, List.of()));

        assertTrue(guard.checkTarget(target(), guard.newContext()).allowed());
    }

    @Test
    void rpmRejectsRequestBeyondLimit() {
        setUpWith(new TrafficPolicies(new RateLimitPolicy(2, 0), List.of()));
        var context = guard.newContext();

        assertTrue(guard.checkTarget(target(), context).allowed());
        assertTrue(guard.checkTarget(target(), context).allowed());

        TrafficDecision rejected = guard.checkTarget(target(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.RATE_LIMIT_RPM, rejected.reason());
        assertEquals(2L, rejected.limit());
        assertEquals(3L, rejected.used());
        assertEquals("model-code", rejected.resourceCode(), "对外只暴露业务码，主键 id 是内部序号");
    }

    @Test
    void resetAtIsWindowEndNotKeyTtl() {
        // 键的 TTL 比窗口多留一个窗口余量；对外告知的重置时刻必须是窗口结束，
        // 否则 Retry-After 会凭空多报一分钟。
        setUpWith(new TrafficPolicies(new RateLimitPolicy(1, 0), List.of()));
        var context = guard.newContext();

        assertTrue(guard.checkTarget(target(), context).allowed());
        TrafficDecision rejected = guard.checkTarget(target(), context);

        long now = System.currentTimeMillis();
        assertTrue(rejected.resetAtMillis() > now, "重置时刻应在未来");
        assertTrue(rejected.resetAtMillis() - now <= 60_000L, "重置时刻是窗口结束，不是多留一个窗口的 TTL");
    }

    @Test
    void tpmRejectsOnlyAfterTokensAccumulated() {
        setUpWith(new TrafficPolicies(new RateLimitPolicy(0, 1000), List.of()));
        var context = guard.newContext();

        guard.record(null, target(), usage(600L, null, null), context);
        assertTrue(guard.checkTarget(target(), context).allowed(), "TPM 未累计到上限前应放行");

        guard.record(null, target(), usage(600L, null, null), context);
        TrafficDecision rejected = guard.checkTarget(target(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.RATE_LIMIT_TPM, rejected.reason());
        assertEquals(1000L, rejected.limit());
        assertEquals(1200L, rejected.used());
    }

    @Test
    void quotaTokensRejectsBeyondWindowLimit() {
        setUpWith(new TrafficPolicies(null, List.of(new QuotaPolicy(1000L, null, null, "DAY"))));
        var context = guard.newContext();

        guard.record(null, quotaTarget(), usage(1000L, null, null), context);
        TrafficDecision rejected = guard.checkTarget(quotaTarget(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.QUOTA_TOKENS, rejected.reason());
        assertEquals(1000L, rejected.limit());
        assertEquals(PROVIDER_CODE, rejected.resourceCode(), "供应商账户按 code 对外，不看解析出来的 id");
    }

    @Test
    void quotaCostAccumulatesInScaledUnits() {
        setUpWith(new TrafficPolicies(null, List.of(new QuotaPolicy(0L, new BigDecimal("10.00"), "USD", "MONTH"))));
        var context = guard.newContext();

        guard.record(null, quotaTarget(), usage(100L, new BigDecimal("6.00"), "USD"), context);
        assertTrue(guard.checkTarget(quotaTarget(), context).allowed());

        guard.record(null, quotaTarget(), usage(100L, new BigDecimal("6.00"), "USD"), context);
        TrafficDecision rejected = guard.checkTarget(quotaTarget(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.QUOTA_COST, rejected.reason());
        assertEquals(10_000_000_000_000L, rejected.limit(), "10.00 应换算成 10^12 计数");
        assertEquals(12_000_000_000_000L, rejected.used());
        assertEquals("USD", rejected.currency(), "成本维度必须带币种，否则响应体里的单位无从得知");
    }

    @Test
    void costWithTinyAmountIsNotTruncatedToZero() {
        // 计费侧成本不再定标：6 位小数单价 × 1 token ÷ 1e6 = 1e-12 的极限小额也必须计进额度，
        // 否则反复调用永远触发不了上限。
        setUpWith(new TrafficPolicies(null, List.of(new QuotaPolicy(0L, new BigDecimal("0.000000000002"), "USD", "MONTH"))));
        var context = guard.newContext();

        guard.record(null, quotaTarget(), usage(1L, new BigDecimal("0.000000000001"), "USD"), context);
        assertTrue(guard.checkTarget(quotaTarget(), context).allowed());

        guard.record(null, quotaTarget(), usage(1L, new BigDecimal("0.000000000001"), "USD"), context);
        TrafficDecision rejected = guard.checkTarget(quotaTarget(), context);
        assertFalse(rejected.allowed(), "两次 1e-12 应刚好触到 2e-12 上限");
        assertEquals(2L, rejected.limit());
        assertEquals(2L, rejected.used());
    }

    @Test
    void scaledAmountRoundsHalfUpInsteadOfTruncating() {
        // 超过 12 位小数的输入按 HALF_UP 收敛到整数计数，不能静默截断（会让判定偏松）
        assertEquals(1L, QuotaDimension.toScaledAmount(new BigDecimal("0.0000000000005")));
        assertEquals(0L, QuotaDimension.toScaledAmount(new BigDecimal("0.0000000000004")));
        assertEquals(0L, QuotaDimension.toScaledAmount(null));
    }

    @Test
    void scaledAmountMultipliesRatherThanSetsScale() {
        // setScale(12) 只改小数位，10.00 仍是 10；计数必须真的乘 10^12
        assertEquals(10_000_000_000_000L, QuotaDimension.toScaledAmount(new BigDecimal("10.00")));
        assertEquals(6_000_000_000_000L, QuotaDimension.toScaledAmount(new BigDecimal("6.00")));
        assertEquals(12, QuotaDimension.COST_SCALE, "缩放因子 12 位，兜住 1e-12 的极限单次成本");
    }

    @Test
    void fourDecimalLimitKeepsFineGrainedCounting() {
        // 成本上限只配 4 位（DECIMAL(18,4) / 前端 precision=4），但内部计数精度由
        // 单次成本的最小量级决定：6 位小数单价 × 1 token ÷ 1e6 = 1e-12，不能被截断成 0。
        BigDecimal fourDecimalLimit = new BigDecimal("0.0001");
        assertEquals(100_000_000L, QuotaDimension.toScaledAmount(fourDecimalLimit),
                "4 位上限应换算成 10^8 计数");
        assertEquals(1L, QuotaDimension.toScaledAmount(new BigDecimal("0.000000000001")),
                "计数须保留到 1e-12，极限小额不能被截断成 0");
    }

    @Test
    void scaledAmountRejectsAmountBeyondCountableRange() {
        // 12 位缩放下 long 只能表示到约 922 万；超出必须明确报错，
        // 而不是让 longValueExact 的 ArithmeticException 在热路径上裸奔成 500
        BigDecimal tooBig = new BigDecimal("10000000");
        BusinessException failure = assertThrows(BusinessException.class,
                () -> QuotaDimension.toScaledAmount(tooBig));
        assertTrue(failure.getMessage().contains("countable range"), failure.getMessage());
        // 边界值本身仍可表示
        assertEquals(Long.MAX_VALUE, QuotaDimension.toScaledAmount(new BigDecimal("9223372.036854775807")));
    }

    @Test
    void costIsNotAccumulatedWhenCurrencyMismatches() {
        setUpWith(new TrafficPolicies(null, List.of(new QuotaPolicy(0L, new BigDecimal("10.00"), "CNY", "MONTH"))));
        var context = guard.newContext();

        guard.record(null, quotaTarget(), usage(100L, new BigDecimal("600.00"), "USD"), context);
        assertTrue(guard.checkTarget(quotaTarget(), context).allowed(), "币种不符不应累加成本");
    }

    @Test
    void modelResourceIgnoresQuotaPolicy() {
        // 模型层只认限流：即便策略里塞了额度也不参与判定
        setUpWith(new TrafficPolicies(null, List.of(new QuotaPolicy(1L, null, null, "DAY"))));
        var context = guard.newContext();

        guard.record(null, target(), usage(5000L, null, null), context);
        assertTrue(guard.checkTarget(target(), context).allowed());
    }

    private void setUpWith(TrafficPolicies policies) {
        TrafficPolicySource policySource = new TrafficPolicySource() {

            @Override
            public TrafficPolicies policiesOf(String resourceType, Long resourceId) {
                return policies;
            }

            @Override
            public Long providerAccountIdOf(String providerAccountCode) {
                return PROVIDER_ID;
            }
        };
        guard = new GatewayTrafficGuard(policySource, new CaffeineTrafficCounterStore(1_000L),
                new TrafficCounterKeys("test", ZoneId.of("Asia/Shanghai")));
    }

    private static TrafficUsage usage(long tokens, BigDecimal cost, String currency) {
        return TrafficUsage.of(tokens, cost, currency);
    }

    /**
     * 限流维度的目标：模型层，命中 MODEL 资源。
     */
    private RoutingRuleTargetDto target() {
        return new RoutingRuleTargetDto(MODEL, MODEL_ID, "model-code", "upstream-model",
                "模型", "CHAT", null, 1, true);
    }

    /**
     * 限额维度的目标：只带供应商账户（targetId 传 null 使资源列表里只剩 PROVIDER），
     * 因为模型 / 路由规则层按设计不参与限额判定。
     */
    private RoutingRuleTargetDto quotaTarget() {
        return new RoutingRuleTargetDto(PROVIDER, PROVIDER_ID, "account-code", "upstream-model",
                "供应商账户", "CHAT", PROVIDER_CODE, 1, true);
    }
}
