package org.chobit.knot.gateway.vo.provider;

public record DiscountPolicy(
        Long id,
        String discountType,
        double discountValue,
        Integer status
) {
}
