package org.chobit.knot.gateway.entity;

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
    private String remark;
}
