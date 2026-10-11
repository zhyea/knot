package org.chobit.knot.gateway.dto.provider;

public record DiscountPolicyDto(Long id, String discountType, double discountValue, Integer status) {
}
