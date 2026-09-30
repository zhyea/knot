package org.chobit.knot.gateway.pricing;

import java.math.BigDecimal;

/**
 * 高低峰判定结果：相位 + 判定原因 + 倍率。
 *
 * <p>不可变值对象，供预览接口与价格计算共用。
 */
public record PhaseDecision(PricingPhase phase,
                            PhaseReason reason,
                            BigDecimal multiplier) {

    /** 不调整：时间缺失时的兜底判定（倍率固定 1，结果等同普通固定价） */
    public static PhaseDecision noTimestamp() {
        return new PhaseDecision(PricingPhase.OFF_PEAK, PhaseReason.NO_TIMESTAMP, BigDecimal.ONE);
    }
}
