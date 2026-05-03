package org.chobit.knot.gateway.dto.billing;

import java.math.BigDecimal;

public record BillingRuleDto(String code, String name, BigDecimal unitPrice, String unit) {
}
