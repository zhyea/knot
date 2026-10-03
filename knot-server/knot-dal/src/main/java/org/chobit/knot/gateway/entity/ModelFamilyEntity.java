package org.chobit.knot.gateway.entity;

import lombok.Data;

/**
 * 模型族（{@code ks_enum_configs} 中 category='model_family' 的枚举项）。
 *
 * <p>模型族仍存枚举表，只是按模型域单独维护；这里用模型域语义字段（code / name）暴露，
 * 避免与通用枚举的 itemCode / itemLabel 混用。</p>
 */
@Data
public class ModelFamilyEntity {
    private Long id;
    /** 族编码（item_code）：统一模型 kb_logical_models.model_family 与计费规则 kb_billing_rules.model_family 引用该值 */
    private String code;
    /** 族显示名（item_label） */
    private String name;
    private Integer sortOrder;
    private Boolean enabled;
    private String remark;
    /** 派生列：被统一模型 / 计费规则引用次数；大于 0 时禁止删除 */
    private Integer usageCount;
}
