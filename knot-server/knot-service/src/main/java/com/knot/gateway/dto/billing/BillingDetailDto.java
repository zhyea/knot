package com.knot.gateway.dto.billing;

import java.math.BigDecimal;

public record BillingDetailDto(String requestId, String appId, String modelCode, int tokenUsage, BigDecimal cost) {
}
