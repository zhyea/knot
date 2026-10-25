package org.chobit.knot.svc.vo.provider;

import java.math.BigDecimal;

/**
 * 折扣策略对外视图：与 {@code kb_provider_discount_policies.discount_value DECIMAL(10,4)} 同型。
 *
 * <p>⚠ <b>折扣仅供展示，不参与计费</b>：本视图只用于管理面展示与增删改，
 * 计费链路不读它，不要把折扣值接进金额计算。</p>
 *
 * <p>折扣值不降精度成 {@code double}：JSON 数值直达 {@link BigDecimal}，
 * 落库与回读都不经过二进制浮点，避免「最短往返表示」这种靠巧合成立的正确性。</p>
 */
public record DiscountPolicy(
        Long id,
        String discountType,
        BigDecimal discountValue,
        Integer status
) {
}
