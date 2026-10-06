package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 供应商信息下拉候选查询。value 语义=code。资源专属过滤：无。
 *
 * <p>⚠ {@code kb_providers} 无 status / is_deleted 列，故 {@code enabledOnly} 与
 * {@code includeDeleted} 对本资源无效（保留字段仅为契约一致）；供应商信息不支持停用，
 * 因此 {@code disabled} 恒为 0，仅「编码不存在」会进 missingValues。</p>
 *
 * <p>约定见 options-refactor-constraints.md 第 0 节（R5 强类型，禁止 Map 透传）。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderProfileOptionQuery(
        Integer pageNum,
        Integer pageSize,
        String keyword,
        List<String> values,
        Boolean enabledOnly,
        Boolean includeDeleted
) {
    /** 归一化为基类（含分页上限钳制 / values 上限校验所需的默认值语义）。 */
    public OptionQuery toBase() {
        return new OptionQuery(pageNum, pageSize, keyword, values, enabledOnly, includeDeleted);
    }
}
