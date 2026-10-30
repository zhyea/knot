package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.svc.dto.routing.BillingCheckCode;
import org.chobit.knot.svc.dto.routing.BillingCheckResult;
import org.chobit.knot.svc.dto.routing.BillingCheckStatus;
import org.chobit.knot.svc.dto.routing.BillingFact;
import org.chobit.knot.svc.dto.routing.BillingFactAmount;
import org.chobit.knot.svc.dto.routing.BillingFactAmountLine;
import org.chobit.knot.svc.dto.routing.BillingFactUsage;
import org.chobit.knot.svc.dto.routing.BillingRuleSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计费自检层（代码精确，权威）：八项纯函数，对 {@link BillingFact} 做确定性校验。
 *
 * <p>判定域 = 算术正确性（可确定性）：明细公式、汇总一致、单位换算、网关账与重算账全等。
 * 三态纪律（v7 §5）：判不了的一律 {@link BillingCheckStatus#SKIP} 并说明原因，绝不充 PASS。</p>
 *
 * <p>本类不触达生产热路径、不读 JEV、不落日志；与 {@link BillingFactAssembler} 解耦，
 * 便于单测逐态覆盖（含篡改金额反向用例与 cost 缺失 SKIP）。</p>
 */
public final class BillingFactChecks {

    /** 相对容差：1 ppm（网关账与重算账同口径，理论全等，留 1 ppm 防 DECIMAL128 尾差） */
    private static final BigDecimal PPM = new BigDecimal("0.000001");

    /** 绝对容差下限：1e-12（金额可小至 1e-9，需低于此才能区分「尾差」与「真实误差」） */
    private static final BigDecimal ABS_TOLERANCE = new BigDecimal("0.000000000001");

    private BillingFactChecks() {
    }

    public static List<BillingCheckResult> runAll(BillingFact fact) {
        List<BillingCheckResult> results = new ArrayList<>();
        results.add(usageNotEmpty(fact));
        results.add(totalTokensConsistent(fact));
        results.add(costFormulaMatch(fact));
        results.add(gatewayRecomputeMatch(fact));
        results.add(ruleModeMatch(fact));
        results.add(ruleSourceExpected(fact));
        results.add(upstreamCostDeviation(fact));
        results.add(asyncSettlementConsistency(fact));
        return List.copyOf(results);
    }

    // ① 静默归零：解析用量非全 0（最危险的计费缺陷）
    private static BillingCheckResult usageNotEmpty(BillingFact fact) {
        BillingFactUsage u = fact.usage();
        boolean empty;
        if (u.tokenBased()) {
            empty = (u.inputTokens() + u.outputTokens() + u.cacheReadTokens() + u.cacheWriteTokens()) <= 0L;
        } else {
            empty = u.genericAmount() <= 0L && u.embeddedTokenSum() <= 0L;
        }
        return empty
                ? BillingCheckResult.fail(BillingCheckCode.USAGE_NOT_EMPTY, "计费用量为零（静默归零）")
                : BillingCheckResult.pass(BillingCheckCode.USAGE_NOT_EMPTY);
    }

    // ② 总量一致：totalTokens == input + output（上游未给由提取器补齐）
    private static BillingCheckResult totalTokensConsistent(BillingFact fact) {
        BillingFactUsage u = fact.usage();
        if (!u.tokenBased() && u.embeddedTokenSum() <= 0L) {
            return BillingCheckResult.skip(BillingCheckCode.TOTAL_TOKENS_CONSISTENT, "非 token 用量，无 token 总量可校验");
        }
        long expected = u.inputTokens() + u.outputTokens();
        if (u.totalTokens() != expected) {
            return BillingCheckResult.fail(BillingCheckCode.TOTAL_TOKENS_CONSISTENT,
                    "total=" + u.totalTokens() + " != input+output=" + expected);
        }
        return BillingCheckResult.pass(BillingCheckCode.TOTAL_TOKENS_CONSISTENT);
    }

    // ③ 公式一致：明细 cost == tokens × price(每百万) ÷ 1e6，且明细汇总 == totalCost
    private static BillingCheckResult costFormulaMatch(BillingFact fact) {
        BillingFactAmount amount = fact.shadowAmount();
        if (amount == null || amount.details() == null || amount.details().isEmpty()) {
            return BillingCheckResult.skip(BillingCheckCode.COST_FORMULA_MATCH, "无计费明细");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BillingFactAmountLine line : amount.details()) {
            BigDecimal cost = safeDecimal(line.cost());
            if (cost == null) {
                continue;
            }
            sum = sum.add(cost);
            BigDecimal expected = lineFormulaCost(line);
            if (expected != null && !moneyEquals(cost, expected)) {
                return BillingCheckResult.fail(BillingCheckCode.COST_FORMULA_MATCH,
                        "明细 " + line.type() + " 金额与「数量×单价(每百万)」不符：cost=" + line.cost()
                                + " 期望≈" + expected.toPlainString());
            }
        }
        if (amount.totalCost() != null) {
            BigDecimal total = safeDecimal(amount.totalCost());
            if (total != null && !moneyEquals(sum, total)) {
                return BillingCheckResult.fail(BillingCheckCode.COST_FORMULA_MATCH,
                        "明细金额汇总(" + sum.toPlainString() + ")与总价(" + amount.totalCost() + ")不一致");
            }
        }
        return BillingCheckResult.pass(BillingCheckCode.COST_FORMULA_MATCH);
    }

    // ④ 网关账 vs 同口径重算账（1 ppm 容差）：抓当次调用算错
    private static BillingCheckResult gatewayRecomputeMatch(BillingFact fact) {
        BillingFactAmount gateway = fact.gatewayBilling();
        BillingFactAmount shadow = fact.shadowAmount();
        if (gateway == null) {
            return BillingCheckResult.skip(BillingCheckCode.GATEWAY_RECOMPUTE_MATCH,
                    "被告账缺失（响应未返回 knot_extend.billing）");
        }
        if (shadow == null) {
            return BillingCheckResult.fail(BillingCheckCode.GATEWAY_RECOMPUTE_MATCH,
                    "重算账为空（未命中计费规则），与被告账不一致");
        }
        BigDecimal gTotal = safeDecimal(gateway.totalCost());
        BigDecimal sTotal = safeDecimal(shadow.totalCost());
        if (gTotal == null || sTotal == null || !moneyEquals(gTotal, sTotal)) {
            return BillingCheckResult.fail(BillingCheckCode.GATEWAY_RECOMPUTE_MATCH,
                    "被告账总价(" + gateway.totalCost() + ")与重算总价(" + shadow.totalCost() + ")不一致");
        }
        Map<String, String> gatewayByType = new LinkedHashMap<>();
        if (gateway.details() != null) {
            for (BillingFactAmountLine line : gateway.details()) {
                gatewayByType.put(line.type(), line.cost());
            }
        }
        if (shadow.details() != null) {
            for (BillingFactAmountLine line : shadow.details()) {
                String gCost = gatewayByType.get(line.type());
                if (gCost == null) {
                    return BillingCheckResult.fail(BillingCheckCode.GATEWAY_RECOMPUTE_MATCH,
                            "被告账缺少明细类型：" + line.type());
                }
                BigDecimal g = safeDecimal(gCost);
                BigDecimal s = safeDecimal(line.cost());
                if (g == null || s == null || !moneyEquals(g, s)) {
                    return BillingCheckResult.fail(BillingCheckCode.GATEWAY_RECOMPUTE_MATCH,
                            "明细 " + line.type() + " 金额不一致：被告=" + gCost + " 重算=" + line.cost());
                }
            }
        }
        return BillingCheckResult.pass(BillingCheckCode.GATEWAY_RECOMPUTE_MATCH);
    }

    // ⑤ 计费模式 vs 用量形态：TOKEN 规则配 token 用量，反之亦然
    private static BillingCheckResult ruleModeMatch(BillingFact fact) {
        if (fact.ruleRef() == null || fact.ruleRef().missing()) {
            return BillingCheckResult.skip(BillingCheckCode.RULE_MODE_MATCH, "未命中计费规则，无法校验模式");
        }
        // billingMode 落的是 BillingModeEnum 的 code，这里只判「是否 token 模式」，
        // 其余模式（IMAGE/AUDIO/VIDEO/...）一律归为非 token 形态
        boolean tokenRule = BillingModeEnum.TOKEN.code().equals(fact.ruleRef().billingMode());
        boolean tokenUsage = fact.usage().tokenBased();
        if (tokenRule == tokenUsage) {
            return BillingCheckResult.pass(BillingCheckCode.RULE_MODE_MATCH);
        }
        return BillingCheckResult.fail(BillingCheckCode.RULE_MODE_MATCH,
                "计费模式(" + fact.ruleRef().billingMode() + ")与用量形态("
                        + (tokenUsage ? "TOKEN" : "非TOKEN") + ")不匹配");
    }

    // ⑥ 规则来源：期望 EXPLICIT（模型静默回退到族/全局默认即 FAIL）
    private static BillingCheckResult ruleSourceExpected(BillingFact fact) {
        BillingRuleSource src = fact.subject() == null ? null : fact.subject().ruleSource();
        if (src == BillingRuleSource.EXPLICIT) {
            return BillingCheckResult.pass(BillingCheckCode.RULE_SOURCE_EXPECTED);
        }
        return BillingCheckResult.fail(BillingCheckCode.RULE_SOURCE_EXPECTED,
                "计费规则未显式绑定，回退至 " + src);
    }

    // ⑦ 上游 cost 量级：knot 费用与上游 cost 相差不超过 2 个数量级（cost 缺失 → SKIP）
    private static BillingCheckResult upstreamCostDeviation(BillingFact fact) {
        BigDecimal upstream = fact.upstreamCost();
        BillingFactAmount gateway = fact.gatewayBilling();
        if (upstream == null) {
            return BillingCheckResult.skip(BillingCheckCode.UPSTREAM_COST_DEVIATION, "上游未返回 cost");
        }
        if (gateway == null || gateway.totalCost() == null) {
            return BillingCheckResult.skip(BillingCheckCode.UPSTREAM_COST_DEVIATION, "被告账缺失");
        }
        BigDecimal knot = safeDecimal(gateway.totalCost());
        if (knot == null || knot.signum() == 0) {
            return BillingCheckResult.skip(BillingCheckCode.UPSTREAM_COST_DEVIATION, "被告账为零");
        }
        if (upstream.signum() == 0) {
            return BillingCheckResult.skip(BillingCheckCode.UPSTREAM_COST_DEVIATION, "上游 cost 为零");
        }
        BigDecimal ratio = upstream.abs().divide(knot.abs(), MathContext.DECIMAL128);
        if (ratio.compareTo(new BigDecimal("0.01")) < 0 || ratio.compareTo(new BigDecimal("100")) > 0) {
            return BillingCheckResult.fail(BillingCheckCode.UPSTREAM_COST_DEVIATION,
                    "knot 费用与上游 cost 相差超过 2 个数量级（ratio=" + ratio.toPlainString() + "）");
        }
        return BillingCheckResult.pass(BillingCheckCode.UPSTREAM_COST_DEVIATION);
    }

    // ⑧ 异步结算一致性：成功必有账、失败不得有账（同步链路 → SKIP）
    private static BillingCheckResult asyncSettlementConsistency(BillingFact fact) {
        String status = fact.subject() == null ? null : fact.subject().taskStatus();
        if (status == null || status.isBlank()) {
            return BillingCheckResult.skip(BillingCheckCode.ASYNC_SETTLEMENT_CONSISTENCY,
                    "同步链路，无异步任务终态可校验");
        }
        boolean settled = fact.gatewayBilling() != null;
        if ("completed".equals(status)) {
            return settled
                    ? BillingCheckResult.pass(BillingCheckCode.ASYNC_SETTLEMENT_CONSISTENCY)
                    : BillingCheckResult.fail(BillingCheckCode.ASYNC_SETTLEMENT_CONSISTENCY,
                            "任务终态成功但没有结算账目（漏收）");
        }
        if ("failed".equals(status)) {
            return settled
                    ? BillingCheckResult.fail(BillingCheckCode.ASYNC_SETTLEMENT_CONSISTENCY,
                            "任务终态失败却落了结算账目（错收）")
                    : BillingCheckResult.pass(BillingCheckCode.ASYNC_SETTLEMENT_CONSISTENCY);
        }
        // queued / processing / unknown：还没到终态，结算本就没发生——判不了，不充 PASS
        return BillingCheckResult.skip(BillingCheckCode.ASYNC_SETTLEMENT_CONSISTENCY,
                "任务未到终态（" + status + "），结算尚未发生");
    }

    /** 明细行金额复算：tokens × price(每百万) ÷ 1e6；缺字段时返回 null（跳过该行） */
    private static BigDecimal lineFormulaCost(BillingFactAmountLine line) {
        if (line.tokens() == null) {
            return null;
        }
        BigDecimal price = safeDecimal(line.price());
        if (price == null) {
            return null;
        }
        return new BigDecimal(line.tokens())
                .multiply(price)
                .divide(BigDecimal.valueOf(1_000_000L), MathContext.DECIMAL128);
    }

    private static BigDecimal safeDecimal(String text) {
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 金额相等：绝对差 ≤ ABS_TOLERANCE，或相对差 ≤ PPM（防 DECIMAL128 尾差） */
    private static boolean moneyEquals(BigDecimal a, BigDecimal b) {
        BigDecimal diff = a.subtract(b).abs();
        if (diff.compareTo(ABS_TOLERANCE) <= 0) {
            return true;
        }
        BigDecimal scale = a.abs().max(b.abs());
        if (scale.signum() == 0) {
            return true;
        }
        BigDecimal rel = diff.divide(scale, MathContext.DECIMAL128);
        return rel.compareTo(PPM) <= 0;
    }
}
