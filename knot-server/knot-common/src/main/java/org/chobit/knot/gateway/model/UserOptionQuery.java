package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 用户下拉候选查询。value 语义=id。资源专属过滤：无。
 * 约定见 options-refactor-constraints.md 第 0 节（R5 强类型，禁止 Map 透传）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserOptionQuery(
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
