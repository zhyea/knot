package org.chobit.knot.svc.vo.common.meta;

/**
 * 统一模型下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('modelType','modelFamily','status', ...)} 对齐）。
 * 供前端做协议联动（{@code modelType}）与计费族过滤（{@code modelFamily}）。
 */
public record LogicalModelMeta(
        String modelType,
        String modelFamily,
        Integer status
) {
}
