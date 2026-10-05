package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        setUpWith(new TrafficPolicies(null, null));

        assertTrue(guard.checkTarget(target(), guard.newContext()).allowed());
    }

    @Test
    void rpmRejectsRequestBeyondLimit() {
        setUpWith(new TrafficPolicies(new RateLimitPolicy(2, 0), null));
        var context = guard.newContext();

        assertTrue(guard.checkTarget(target(), context).allowed());
        assertTrue(guard.checkTarget(target(), context).allowed());

        TrafficDecision rejected = guard.checkTarget(target(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.RATE_LIMIT_RPM, rejected.reason());
        assertEquals(2L, rejected.limit());
        assertEquals(3L, rejected.used());
    }

    @Test
    void tpmRejectsOnlyAfterTokensAccumulated() {
        setUpWith(new TrafficPolicies(new RateLimitPolicy(0, 1000), null));
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
        setUpWith(new TrafficPolicies(null, new QuotaPolicy(1000L, null, null, "DAY")));
        var context = guard.newContext();

        guard.record(null, quotaTarget(), usage(1000L, null, null), context);
        TrafficDecision rejected = guard.checkTarget(quotaTarget(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.QUOTA_TOKENS, rejected.reason());
        assertEquals(1000L, rejected.limit());
    }

    @Test
    void quotaCostAccumulatesInScaledUnits() {
        setUpWith(new TrafficPolicies(null, new QuotaPolicy(0L, new BigDecimal("10.00"), "USD", "MONTH")));
        var context = guard.newContext();

        guard.record(null, quotaTarget(), usage(100L, new BigDecimal("6.00"), "USD"), context);
        assertTrue(guard.checkTarget(quotaTarget(), context).allowed());

        guard.record(null, quotaTarget(), usage(100L, new BigDecimal("6.00"), "USD"), context);
        TrafficDecision rejected = guard.checkTarget(quotaTarget(), context);
        assertFalse(rejected.allowed());
        assertEquals(TrafficRejectReason.QUOTA_COST, rejected.reason());
        assertEquals(1_000_000_000L, rejected.limit(), "10.00 应换算成 10^8 纳元计数");
        assertEquals(1_200_000_000L, rejected.used());
    }

    @Test
    void costWithEightDecimalsIsNotTruncatedToZero() {
        // 计费侧 cost() 是 setScale(8, HALF_UP)：单价 1e-8 的请求成本必须被计进额度，
        // 否则反复调用永远触发不了上限。
        setUpWith(new TrafficPolicies(null, new QuotaPolicy(0L, new BigDecimal("0.00000002"), "USD", "MONTH")));
        var context = guard.newContext();

        guard.record(null, quotaTarget(), usage(1L, new BigDecimal("0.00000001"), "USD"), context);
        assertTrue(guard.checkTarget(quotaTarget(), context).allowed());

        guard.record(null, quotaTarget(), usage(1L, new BigDecimal("0.00000001"), "USD"), context);
        TrafficDecision rejected = guard.checkTarget(quotaTarget(), context);
        assertFalse(rejected.allowed(), "两次 1e-8 应刚好触到 2e-8 上限");
        assertEquals(2L, rejected.limit());
        assertEquals(2L, rejected.used());
    }

    @Test
    void scaledAmountRoundsHalfUpInsteadOfTruncating() {
        // 超过 8 位小数的输入按 HALF_UP 收敛到整数计数，不能静默截断（会让判定偏松）
        assertEquals(2L, QuotaDimension.toScaledAmount(new BigDecimal("0.000000015")));
        assertEquals(1L, QuotaDimension.toScaledAmount(new BigDecimal("0.000000014")));
        assertEquals(0L, QuotaDimension.toScaledAmount(null));
    }

    @Test
    void scaledAmountMultipliesRatherThanSetsScale() {
        // setScale(8) 只改小数位，10.00 仍是 10；计数必须真的乘 10^8
        assertEquals(1_000_000_000L, QuotaDimension.toScaledAmount(new BigDecimal("10.00")));
        assertEquals(600_000_000L, QuotaDimension.toScaledAmount(new BigDecimal("6.00")));
        assertEquals(8L, QuotaDimension.COST_SCALE, "缩放因子固定 8 位（与计费侧 setScale(8) 对齐）");
    }

    @Test
    void fourDecimalLimitKeepsEightDecimalCounting() {
        // 成本上限只配 4 位（DECIMAL(18,4) / 前端 precision=4），但内部计数必须仍是 1e-8：
        // 若因子降到 4，单次 1e-8 成本截断成 0，额度永远不累加。
        BigDecimal fourDecimalLimit = new BigDecimal("0.0001");
        assertEquals(10_000L, QuotaDimension.toScaledAmount(fourDecimalLimit),
                "4 位上限应换算成 10^4 计数");
        assertEquals(1L, QuotaDimension.toScaledAmount(new BigDecimal("0.00000001")),
                "计数仍须保留 8 位，单次 1e-8 成本不能被截断成 0");
    }

    @Test
    void costIsNotAccumulatedWhenCurrencyMismatches() {
        setUpWith(new TrafficPolicies(null, new QuotaPolicy(0L, new BigDecimal("10.00"), "CNY", "MONTH")));
        var context = guard.newContext();

        guard.record(null, quotaTarget(), usage(100L, new BigDecimal("600.00"), "USD"), context);
        assertTrue(guard.checkTarget(quotaTarget(), context).allowed(), "币种不符不应累加成本");
    }

    @Test
    void modelResourceIgnoresQuotaPolicy() {
        // 模型层只认限流：即便策略里塞了额度也不参与判定
        setUpWith(new TrafficPolicies(null, new QuotaPolicy(1L, null, null, "DAY")));
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
