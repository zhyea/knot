package org.chobit.knot.gateway.dal.entity;

import lombok.Data;

@Data
public class EnumConfigEntity {
    private Long id;
    /** 分类 ID（落库） */
    private Long categoryId;
    /** 分类编码（JOIN / 接口入参，非表字段） */
    private String category;
    private String itemCode;
    private String itemLabel;
    private Integer sortOrder;
    private Boolean isSystem;
    private Boolean isEnabled;
    private Boolean isDeleted;
    private String remark;
    /**
     * 枚举项编码的绑定处数（派生字段，不落库）：计费规则的 model_family、统一模型的
     * model_family、折扣策略的 discount_type、敏感词条的 category_code 都按 item_code 引用。
     * &gt;0 时禁止改码。
     */
    private Long boundCount;
}
