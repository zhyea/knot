package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RateLimitPolicyEntity {
    private Long id;
    private String policyCode;
    private String policyName;
    private Integer rpm;
    private Integer tpm;
    private String status;
    private String remark;
}
