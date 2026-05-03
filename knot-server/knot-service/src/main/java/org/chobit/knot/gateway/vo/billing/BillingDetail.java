package org.chobit.knot.gateway.vo.billing;

import java.math.BigDecimal;

public record BillingDetail(String requestId, String appId, String modelCode, int tokenUsage, BigDecimal cost) {
}
