package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BillingRuleEntity {
    private Long id;
    private String code;
    private String name;
    private Long providerId;
    private String providerName;
    private Long logicalModelId;
    private String logicalModelName;
    private Long currentVersionId;
    private String remark;
    private String status;
    private Boolean deleted;

    /** Current/active version fields for list and audit display. */
    private Integer versionNo;
    private String versionName;
    private String billingMode;
    private String currency;
    private String unit;
    private BigDecimal unitPrice;
    private String itemType;
    private String configJson;
    private String ladderJson;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
