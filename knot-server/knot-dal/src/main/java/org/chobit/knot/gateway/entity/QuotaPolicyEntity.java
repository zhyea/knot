package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuotaPolicyEntity {
    private Long id;
    private String policyCode;
    private String policyName;
    private Long maxTokens;
    private BigDecimal costLimit;
    private String currency;
    private String quotaWindow;
    private String status;
    private String remark;
}
