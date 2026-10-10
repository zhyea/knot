package org.chobit.knot.gateway.vo.provider;

public record DiscountPolicy(
        Long id,
        String policyName,
        String scopeType,
        String scopeRefCode,
        String discountType,
        double discountValue,
        int priority,
        Integer status
) {
}
