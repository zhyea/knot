package org.chobit.knot.common.model;

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
        /** 插件执行类型精确匹配（PluginTypeEnum：SYNC）；来自插件列表「类型」筛选 */
        String pluginType,
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

    /**
     * null 安全的分页转换：{@code query == null} 时给默认分页。
     *
     * 默认页大小的唯一来源是 {@link PageRequest#of(Integer, Integer)}（{@code null} → 1 / 20）。
     */
    public static PageRequest toPageRequest(PageQuery query) {
        return query == null ? PageRequest.of(null, null) : query.toPageRequest();
    }

    /**
     * 空查询：所有筛选项为 {@code null}，供「请求体未传」时占位。
     */
    public static PageQuery empty() {
        return new PageQuery(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    /** {@code null} → {@link #empty()}；否则原样返回。 */
    public static PageQuery orEmpty(PageQuery query) {
        return query == null ? empty() : query;
    }
}
