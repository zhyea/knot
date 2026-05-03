package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DiscountPolicyEntity {
    private Long id;
    private Long providerId;
    private String policyName;
    private String scopeType;
    private Long scopeRefId;
    private String discountType;
    private BigDecimal discountValue;
    private Integer priority;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
    private String remark;
}
