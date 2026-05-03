package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BillingRuleEntity {
    private Long id;
    private String code;
    private String name;
    private String billingMode;
    private String unit;
    private BigDecimal unitPrice;
    private String status;
    private LocalDateTime effectiveFrom;
}
