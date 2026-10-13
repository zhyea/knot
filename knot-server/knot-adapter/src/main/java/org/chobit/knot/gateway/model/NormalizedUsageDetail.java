package org.chobit.knot.gateway.model;

import java.math.BigDecimal;

/**
 * 计费明细行：一类计费量的金额结果。
 *
 * <p>{@code cost} 是十进制普通数字符串（8 位小数定标），不是数值：Jackson 默认按
 * {@link BigDecimal#toString()} 序列化数值，调整指数小于 -6 的小额会写成科学计数法
 * （如 {@code 1E-8}），下游解析口径不一。字符串在
 * {@code AbstractBillingModeCalculator#plainMoney} 统一以
 * {@link BigDecimal#toPlainString()} 定档后进入本记录，永不出科学计数法。</p>
 */
public record NormalizedUsageDetail(String type,
                                    Long tokens,
                                    String cost,
                                    BigDecimal price) {
}
