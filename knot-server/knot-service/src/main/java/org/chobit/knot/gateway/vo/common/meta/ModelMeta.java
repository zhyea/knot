package org.chobit.knot.gateway.vo.common.meta;

/**
 * 供应商模型下拉候选项的 meta（与 {@code OptionsMapper.xml} 的 {@code json_object(...)} 对齐）。
 *
 * <p>承载供应商 / 统一模型派生字段，供池内表格与表单联动展示（如供应商名、所属统一模型、协议类型），
 * 不返回 model 凭据或完整 configJson（见 options-refactor-constraints.md 第 0 节）。</p>
 */
public record ModelMeta(
        String providerName,
        String providerAccountCode,
        String modelName,
        String name,
        String modelType,
        String logicalModelCode,
        Integer status
) {
}
