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
    private String modelFamilyCode;
    private String modelFamilyName;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用；删除语义见 isDeleted */
    private Integer status;
    /** 逻辑删除：0-否 1-是（删除时同时置 status=0，恢复后保持停用） */
    private Integer isDeleted;
    private String remark;

    /** 当前版本（最近生效版本）字段，用于列表与审计展示 */
    private String versionCode;
    private String uniqHash;
    /** 当前版本状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer versionStatus;
    private String billingMode;
    /** 进阶定价方案（PricingPlanEnum）：FIXED/TIERED/PEAK_OFF_PEAK */
    private String pricingPlan;
    private String currency;
    private String unit;
    private String configJson;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    /** 只读派生：绑定了该规则编码的供应商模型数（改编码/停用/删除前据此判断可否操作） */
    private Long boundModelCount;
}
