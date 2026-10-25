package org.chobit.knot.svc.dto.provider;

import java.math.BigDecimal;

/**
 * 折扣策略（绑定供应商模型业务码）。
 *
 * <p><b>折扣仅供展示，不参与任何计费计算</b>（robin 2026-10-25 明确）：
 * 金额链路全程不读本记录，改这里不会影响出账结果。</p>
 *
 * <p>{@code discountValue} 仍走 {@link BigDecimal}，理由只是与库列
 * {@code DECIMAL(10,4)} 同型、省掉一次无谓的十进制->二进制->十进制往返：
 * {@code double} 会引入 {@code BigDecimal.valueOf(double)} 依赖
 * {@code Double.toString} 最短往返表示这种靠巧合成立的正确性，且 {@code 1e400}
 * 这类输入会先变成 {@code Infinity} 再抛 {@link NumberFormatException} 直接 500。
 * 这不是为「将来参与计费」铺路——按上述口径，将来也不会参与。</p>
 */
public record DiscountPolicyDto(Long id, String discountType, BigDecimal discountValue, Integer status) {
}
