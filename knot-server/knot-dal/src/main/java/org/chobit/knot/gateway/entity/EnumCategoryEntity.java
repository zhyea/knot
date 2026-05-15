package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class EnumCategoryEntity {
    private Long id;
    private String category;
    private String categoryName;
    private String description;
    /** 系统内置分类：其下枚举项不可删改 */
    private Boolean isSystem;
    private Boolean isEnabled;
}
