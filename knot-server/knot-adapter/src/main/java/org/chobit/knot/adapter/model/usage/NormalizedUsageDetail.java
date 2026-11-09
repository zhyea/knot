package org.chobit.knot.adapter.model.usage;

/**
 * 计费明细行：一类计费量的金额结果。
 *
 * <p>{@code cost} 与 {@code price} 都是十进制普通数字符串，不是数值：Jackson 默认按
 * {@link java.math.BigDecimal#toString()} 序列化数值，调整指数小于 -6 的小额会写成科学计数法
 * （如 {@code 1E-8}），下游解析口径不一。两者在 {@link NormalizedBillingAmount#plainMoney} 统一以
 * {@link java.math.BigDecimal#toPlainString()} 定档后进入本记录，永不出科学计数法。</p>
 *
 * <p>字符串同样意味着不设小数位上限——单价常见 6 位小数，除百万量级后需要 12 位以上才不归零。</p>
 *
 * <p>⭐ {@code quantity} 是<b>计费量</b>，不是 token；量纲由同一行的 {@code unit}
 * （{@code BillingUnitEnum} 编码）给出：视频按秒、图片按张、token 按个。所有明细行必须经
 * {@code AbstractBillingModeCalculator.detail()} 构造，禁止调用处自行拼本 record ——
 * 少了 unit 的量在下游无法解释。</p>
 */
public record NormalizedUsageDetail(String type,
                                    Long quantity,
                                    String unit,
                                    String cost,
                                    String price) {
}
