package org.chobit.knot.gateway.dto.provider;

public record DiscountPolicyDto(Long id, String policyName, String scopeType, String scopeRefCode,
                                String discountType, double discountValue, int priority, Integer status) {
}
