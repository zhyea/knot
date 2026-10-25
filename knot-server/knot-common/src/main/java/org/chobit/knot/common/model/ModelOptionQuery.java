package org.chobit.knot.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 供应商模型下拉候选查询。value 语义=modelCode。资源专属过滤：按统一模型过滤；按模型族过滤；按供应商账户过滤。
 * 约定见 options-refactor-constraints.md 第 0 节（R5 强类型，禁止 Map 透传）。
 *
 * <p>{@code logicalModelCode} 为硬过滤（模型池编辑场景：池内模型必须归属该统一模型）；
 * {@code preferredLogicalModelCode} 为纯排序（路由规则场景：命中项置前，其余项仍可出现），两者语义分离。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelOptionQuery(
        Integer pageNum,
        Integer pageSize,
        String keyword,
        List<String> values,
        Boolean enabledOnly,
        Boolean includeDeleted,
        String logicalModelCode,
        String modelFamilyCode,
        String providerAccountCode,
        /** 优先排序统一模型 code：仅排序不过滤，命中项置前（路由规则目标选择器场景） */
        String preferredLogicalModelCode
) {
    /** 归一化为基类（含分页上限钳制 / values 上限校验所需的默认值语义）。 */
    public OptionQuery toBase() {
        return new OptionQuery(pageNum, pageSize, keyword, values, enabledOnly, includeDeleted);
    }
}
