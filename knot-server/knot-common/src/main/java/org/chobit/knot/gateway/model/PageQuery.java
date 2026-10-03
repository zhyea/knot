package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 通用 POST 分页查询请求体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageQuery(
        Integer pageNum,
        Integer pageSize,
        String category,
        String keyword,
        String tag,
        String status,
        Boolean enabled,
        List<String> modelTypes,
        Long parentId,
        String modelFamilyCode,
        String logicalModelCode,
        String protocol,
        /** 管理列表是否包含已逻辑删除的记录（默认 false）；下拉/选择类查询不传，保持只出未删除项 */
        Boolean includeDeleted,
        /** 业务码精确匹配（不是 like 模糊搜索）；用于「取回已绑定的那一条」这类回显场景 */
        String code
) {
    /**
     * Converts the query to a page request.
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
