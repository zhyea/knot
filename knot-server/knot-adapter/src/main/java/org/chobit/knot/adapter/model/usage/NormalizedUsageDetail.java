package org.chobit.knot.adapter.model.usage;

/**
 * 计费明细行：一类计费量的金额结果。
 *
 * <p>{@code cost} 与 {@code price} 都是十进制普通数字符串，不是数值：Jackson 默认按
 * {@link java.math.BigDecimal#toString()} 序列化数值，调整指数小于 -6 的小额会写成科学计数法
 * （如 {@code 1E-8}），下游解析口径不一。两者在 {@link NormalizedBillingAmount#plainMoney} 统一以
 * {@link java.math.BigDecimal#toPlainString()} 定档后进入本记录，永不出科学计数法。</p>
 *
 * <p>字符串同样意味着不设小数位上限——小额 {@code cost} 由「用量 × 单价 ÷ 单位基数」产生，
 * 常见 6 位小数单价在 {@code 1M_TOKENS} 下需要 12 位以上才不归零。</p>
 *
 * <p>⭐ {@code quantity} 是<b>计费量</b>，不是 token；量纲由同一行的 {@code unit}
 * （{@code BillingUnitEnum} 编码）给出：视频按秒、图片按张、token 按个。所有明细行必须经
 * {@code AbstractBillingModeCalculator.detail()} 构造，禁止调用处自行拼本 record ——
 * 少了 unit 的量在下游无法解释。</p>
 *
 * <p>⭐ {@code price} 是规则配置的<b>原始单价</b>，基准就是同一行的 {@code unit}
 * （{@code PER_SECOND} = 每秒价、{@code 1M_TOKENS} = 每百万 Token 价），不折算成每百万单位。
 * 复算式 {@code cost = quantity × price ÷ unitSize(unit)}，分母由 {@code unit} 推导；
 * 因此 {@code unit} 不只是说明字段，而是金额复算的依赖，不能写死。</p>
 */
public record NormalizedUsageDetail(String type,
                                    Long quantity,
                                    String unit,
                                    String cost,
                                    String price) {
}
