package org.chobit.knot.svc.vo.system;

/**
 * 枚举分类聚合行（枚举管理左侧分类列表）。
 *
 * <p>这不是一张表的映射，是 {@code EnumConfigMapper.listCategorySummaries} 的
 * <b>聚合投影</b>（分类 ⋈ 项数的 group by 结果）。原先它的类型
 * {@code dal.entity.EnumCategorySummary} 住在 dal 里、被 admin 直接当响应体——
 * 一个纯查询投影却按「实体」外泄。现在收成 svc 的载体。</p>
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
