package org.chobit.knot.gateway.entity;

import lombok.Data;

/**
 * 枚举分类聚合（用于分类列表页）
 */
@Data
public class EnumCategorySummary {
    private String category;
    /** 分类显示名 */
    private String categoryName;
    /** 是否系统内置分类（其下枚举项不可删改） */
    private Boolean isSystem;
    private Long itemCount;
    private Long enabledCount;
}
