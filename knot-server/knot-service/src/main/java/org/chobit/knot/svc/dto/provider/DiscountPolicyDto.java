package org.chobit.knot.svc.dto.provider;

import java.math.BigDecimal;

/**
 * 折扣策略（绑定供应商模型业务码）。
 *
 * 折扣仅供展示，不参与任何计费计算；改这里不影响出账结果。
 * {@code discountValue} 用 {@link BigDecimal}，与库列 {@code DECIMAL(10,4)} 同型。
 */
public record DiscountPolicyDto(Long id, String discountType, BigDecimal discountValue, Integer status) {
}
