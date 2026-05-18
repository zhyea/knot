package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RateLimitPolicyEntity {
    private Long id;
    private String policyCode;
    private String policyName;
    private Integer perSecond;
    private Integer perMinute;
    private String timeWindow;
    private String status;
    private String remark;
}
