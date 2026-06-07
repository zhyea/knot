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
        String status,
        List<String> modelTypes,
        Long parentId,
        Long providerId,
        Long logicalModelId
) {
    /**
     * Converts the query to a page request.
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
