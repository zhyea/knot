package org.chobit.knot.gateway.vo.billing;

import java.math.BigDecimal;

public record BillingRule(Long id, String code, String name, BigDecimal unitPrice, String unit, boolean enabled) {
}
