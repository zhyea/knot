package org.chobit.knot.gateway.dal.entity;

import lombok.Data;

@Data
public class RateLimitPolicyEntity {
    private Long id;
    private String policyCode;
    private String policyName;
    private Integer rpm;
    private Integer tpm;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private String remark;
}
