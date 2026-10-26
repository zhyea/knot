package org.chobit.knot.dal.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计费规则版本：一个版本保存一份完整计费配置（config_json）。
 *
 * <p>按 {@code ruleId}（规则主键）归属规则主体，不按 {@code code} 关联：
 * 版本是主体的从属明细，与主体同生命周期；规则编码是可改的业务标识，
 * 一旦被版本行引用就会把编码锁死（并让改码时分不清版本行归属）。</p>
 */
@Data
public class BillingRuleVersionEntity {
    private Long id;
    /** 计费规则主键（kb_billing_rules.id），非规则业务码 */
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
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
