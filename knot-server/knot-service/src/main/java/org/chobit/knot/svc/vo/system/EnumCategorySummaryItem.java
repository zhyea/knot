package org.chobit.knot.svc.vo.system;

/**
 * 枚举分类聚合行（枚举管理左侧分类列表）。
 * 非单表映射，而是 {@code EnumConfigMapper.listCategorySummaries} 的聚合投影（分类 ⋈ 项数的 group by 结果）。
 */
public record EnumCategorySummaryItem(
        String category,
        /** 分类显示名 */
        String categoryName,
        /** 是否系统内置分类（其下枚举项不可删改） */
        Boolean isSystem,
        Long itemCount,
        Long enabledCount
) {
}
