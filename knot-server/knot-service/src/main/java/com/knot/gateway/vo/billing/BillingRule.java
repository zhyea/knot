package com.knot.gateway.vo.billing;

import java.math.BigDecimal;

public record BillingRule(String code, String name, BigDecimal unitPrice, String unit) {
}
