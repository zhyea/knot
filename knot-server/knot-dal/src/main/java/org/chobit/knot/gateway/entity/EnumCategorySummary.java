package org.chobit.knot.gateway.entity;

/**
 * 枚举分类聚合（用于分类列表页）
 */
public record EnumCategorySummary(String category, Long itemCount, Long enabledCount) {
}
