package org.chobit.knot.gateway.traffic;

import java.math.BigDecimal;

/**
 * 一次调用的用量：限额与 TPM 维度都按它记账。
 *
 * @param tokens   本次消耗 token 数，取不到时为 {@code 0}
 * @param cost     本次计费成本，未命中计费规则时为 {@code null}
 * @param currency 成本币种（USD / CNY），与成本同时为空
 */
public record TrafficUsage(long tokens, BigDecimal cost, String currency) {

    /**
     * 没有任何可用用量。
     */
    public static final TrafficUsage EMPTY = new TrafficUsage(0L, null, null);

    /**
     * 构造用量；金额或币种缺失时成本维度不参与记账。
     */
    public static TrafficUsage of(long tokens, BigDecimal cost, String currency) {
        boolean usable = cost != null && currency != null && !currency.isBlank();
        return new TrafficUsage(Math.max(0L, tokens), usable ? cost : null, usable ? currency : null);
    }

    /**
     * 是否有可用于成本维度记账的金额。
     */
    public boolean hasCost() {
        return cost != null && currency != null;
    }
}
