package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计费规则主体 + 当前版本（最近生效版本）展示字段。
 * 规则表只存身份与绑定；价格配置在版本 config_json 中。
 */
@Data
public class BillingRuleEntity {
    private Long id;
    private String code;
    private String logicalModelCode;
    private String logicalModelName;
    /** 生命周期状态：ACTIVE/INACTIVE/DELETED，查询排除 DELETED */
    private String status;
    private String remark;

    /** 当前版本（最近生效版本）字段，用于列表与审计展示 */
    private String versionCode;
    private String uniqHash;
    /** 当前版本状态：ACTIVE/DISABLED */
    private String versionStatus;
    private String billingMode;
    /** 进阶定价方案（PricingPlanEnum）：FIXED/TIERED/PEAK_OFF_PEAK */
    private String pricingPlan;
    private String currency;
    private String unit;
    private String configJson;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
