package org.chobit.knot.common.pricing;

import org.chobit.knot.common.model.BillingConfig;

import java.math.BigDecimal;

/**
 * 高低峰判定结果：相位 + 判定原因 + 倍率 + 命中相位规则，不可变值对象，供预览接口与价格计算共用。
 * {@code matchedRule} 用于 ABSOLUTE 模式直接取命中相位自身单价，MULTIPLIER 模式忽略；无时间来源时为 {@code null}。
 */
public record PhaseDecision(PricingPhase phase,
                            PhaseReason reason,
                            BigDecimal multiplier,
                            BillingConfig.PhaseRule matchedRule) {

    /** 不调整：时间缺失时的兜底判定（倍率固定 1，结果等同普通固定价） */
    public static PhaseDecision noTimestamp() {
        return new PhaseDecision(PricingPhase.OFF_PEAK, PhaseReason.NO_TIMESTAMP, BigDecimal.ONE, null);
    }
}
