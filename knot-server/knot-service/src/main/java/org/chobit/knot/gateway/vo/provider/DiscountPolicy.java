package org.chobit.knot.gateway.vo.provider;

public record DiscountPolicy(
        Long id,
        String policyName,
        String scopeType,
        Long scopeRefId,
        String discountType,
        double discountValue,
        int priority,
        String status
) {
}
