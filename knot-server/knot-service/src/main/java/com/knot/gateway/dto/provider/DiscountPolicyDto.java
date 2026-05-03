package com.knot.gateway.dto.provider;

public record DiscountPolicyDto(Long id, String policyName, String scopeType, Long scopeRefId,
                                String discountType, double discountValue, int priority, String status) {
}
