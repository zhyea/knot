package org.chobit.knot.gateway.dto.billing;

import java.math.BigDecimal;

public record BillingRuleDto(Long id, String code, String name, BigDecimal unitPrice, String unit, boolean enabled) {
}
