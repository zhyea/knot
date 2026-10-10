package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DiscountPolicyEntity {
    private Long id;
    /** 绑定的供应商模型业务码（kb_models.model_code），非主键 id */
    private String modelCode;
    private String policyName;
    private String scopeType;
    private Long scopeRefId;
    private String discountType;
    private BigDecimal discountValue;
    private Integer priority;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private String remark;
}
