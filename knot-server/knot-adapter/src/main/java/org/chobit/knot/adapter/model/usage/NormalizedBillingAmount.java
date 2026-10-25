package org.chobit.knot.adapter.model.usage;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一次计费的结果：总价与计费明细。
 *
 * <p>{@code totalCost} 与明细 {@link NormalizedUsageDetail#cost()} 同为**十进制普通字符串**契约：
 * Jackson 默认按 {@link BigDecimal#toString()} 写数值，调整指数小于 -6 的小额会变成科学计数法
 * （如 {@code 1E-8}），下游解析口径不一；字符串在 {@link #plainMoney(BigDecimal)} 统一以
 * {@link BigDecimal#toPlainString()} 定档后进入本记录，永不出科学计数法。</p>
 *
 * <p>字符串同时意味着**不设小数位上限**：计费单价常见值已是 6 位小数，再除百万量级，
 * 8 位定标会把小额真实成本抹成 0（单价 0.0000015 × 1000 token ÷ 1e6 = 0.0000000015，
 * 8 位下直接归零）。单位基数目前全是 10 的幂（PER_MINUTE=60 已于 2026-10-17 退役），
 * 除法恒定除尽、结果精确；{@code AbstractBillingModeCalculator} 的 DECIMAL128
 * 只为未来可能新增的非 10 的幂基数兜底。</p>
 *
 * <p>小数位数取**精确商的自然位数**，不额外补齐也不截断：单价 0.02 乘出 {@code 0.20}、
 * 单价 2 乘出 {@code 0.8}，都是如实反映，下游按字符串解析数值即可。</p>
 */
public record NormalizedBillingAmount(long totalTokens,
                                      String totalCost,
                                      List<NormalizedUsageDetail> details) {

    /**
     * 金额的对外文本形态：十进制普通串（{@link BigDecimal#toPlainString()}），绝不出科学计数法。
     *
     * <p>全工程只有这一个实现，明细与总价共用，避免两处各写一遍转换。</p>
     *
     * @param value 待转换金额；{@code null} 原样返回 {@code null}（由调用方表达「无金额」）
     */
    public static String plainMoney(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    /** 由 BigDecimal 金额构造；文本化在本方法内一次完成，调用处不再各自 {@code toPlainString()}。 */
    public static NormalizedBillingAmount of(long totalTokens,
                                             BigDecimal totalCost,
                                             List<NormalizedUsageDetail> details) {
        return new NormalizedBillingAmount(totalTokens, plainMoney(totalCost), details);
    }
}
