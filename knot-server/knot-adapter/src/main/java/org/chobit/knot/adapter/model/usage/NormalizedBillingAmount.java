package org.chobit.knot.adapter.model.usage;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一次计费的结果：总价与计费明细。
 *
 * {@code totalCost} 与明细 {@link NormalizedUsageDetail#cost()} 同为十进制普通字符串契约：经 {@link #plainMoney(BigDecimal)}
 * 按 {@link BigDecimal#toPlainString()} 定档，绝不出科学计数法，不设小数位上限。
 * 小数位数取精确商的自然位数，不补齐也不截断。
 */
public record NormalizedBillingAmount(long totalTokens,
                                      String totalCost,
                                      List<NormalizedUsageDetail> details) {

    /**
     * 金额的对外文本形态：十进制普通串（{@link BigDecimal#toPlainString()}），绝不出科学计数法；明细与总价共用本方法。
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
