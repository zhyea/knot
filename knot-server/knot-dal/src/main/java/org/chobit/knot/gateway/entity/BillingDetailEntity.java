package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillingDetailEntity {
    private String requestId;
    private String appId;
    private String modelCode;
    private Integer totalTokens;
    private BigDecimal costAmount;
}
