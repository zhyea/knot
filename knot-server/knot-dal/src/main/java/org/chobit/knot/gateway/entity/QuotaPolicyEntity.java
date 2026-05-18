package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class QuotaPolicyEntity {
    private Long id;
    private String policyCode;
    private String policyName;
    private Long dailyLimit;
    private Long monthlyLimit;
    private Long tokenLimit;
    private Boolean alertEnabled;
    private String status;
    private String remark;
}
