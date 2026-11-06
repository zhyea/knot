package org.chobit.knot.gateway.traffic;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.UsageAccounting;

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
     * 从一次调用的完整账目投影限额/频控用量。
     *
     * <p>token 取归一化总量，成本只认计费结果——没有计费结果就不做估算。
     * 同步、流式终态与异步终态共用这一入口，避免三处各自解析金额字符串而漂移。</p>
     */
    public static TrafficUsage of(UsageAccounting accounting) {
        if (accounting == null) {
            return EMPTY;
        }
        NormalizedUsage normalized = accounting.normalized();
        return of(accounting.totalTokens(), costOf(normalized),
                normalized == null ? null : normalized.currency());
    }

    /**
     * 是否有可用于成本维度记账的金额。
     */
    public boolean hasCost() {
        return cost != null && currency != null;
    }

    /**
     * 计费金额的对外契约是十进制普通字符串；空值或脏值按「无成本」处理。
     */
    private static BigDecimal costOf(NormalizedUsage normalized) {
        if (normalized == null || StringUtils.isBlank(normalized.totalCost())) {
            return null;
        }
        try {
            return new BigDecimal(StringUtils.trim(normalized.totalCost()));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
