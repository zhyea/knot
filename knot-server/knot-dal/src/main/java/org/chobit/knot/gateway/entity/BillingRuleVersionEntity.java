package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BillingRuleVersionEntity {
    private Long id;
    private Long ruleId;
    private Integer versionNo;
    private String versionName;
    private String billingMode;
    private String currency;
    private String configJson;
    private String ladderJson;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String changeReason;
}
