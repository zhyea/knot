package org.chobit.knot.adapter.model.usage;

/**
 * 计费明细行：一类计费量的金额结果；{@code cost} / {@code price} 为十进制普通数字符串（非数值）。
 * {@code quantity} 是计费量（不是 token），量纲由 {@code unit}（{@code BillingUnitEnum} 编码）给出；
 * {@code price} 是规则配置的原始单价、基准即 {@code unit}，复算式 {@code cost = quantity × price ÷ unitSize(unit)}。
 * 明细行须经 {@code AbstractBillingModeCalculator.detail()} 构造，不可自行拼本 record。
 */
public record NormalizedUsageDetail(String type,
                                    Long quantity,
                                    String unit,
                                    String cost,
                                    String price) {
}
