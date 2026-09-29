package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计费规则版本：一个版本保存一份完整计费配置（config_json）。
 */
@Data
public class BillingRuleVersionEntity {
    private Long id;
    private Long ruleId;
    /** 规则内人工可读版本号（v1/v2/2026-01） */
    private String versionCode;
    /** 配置内容指纹（MD5），同一 rule_id 内唯一 */
    private String uniqHash;
    private String billingMode;
    /** 进阶定价方案（PricingPlanEnum）：FIXED/TIERED/PEAK_OFF_PEAK */
    private String pricingPlan;
    private String currency;
    private String unit;
    private String configJson;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
