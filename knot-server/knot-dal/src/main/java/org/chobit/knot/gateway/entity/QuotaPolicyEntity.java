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
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private String remark;
}
