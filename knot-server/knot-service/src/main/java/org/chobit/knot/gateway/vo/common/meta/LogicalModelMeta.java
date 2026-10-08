package org.chobit.knot.gateway.vo.common.meta;

/**
 * 统一模型下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object('modelType','modelFamily','status', ...)} 对齐）。
 *
 * <p>供前端做协议联动（modelType）与计费族过滤（modelFamily）：下拉里选中统一模型后，
 * 这两字段直接可用于联动其他表单控件，不必再单独查详情接口。</p>
 */
public record LogicalModelMeta(
        String modelType,
        String modelFamily,
        Integer status
) {
}
