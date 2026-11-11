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
     * null 安全的分页转换：请求体未传（{@code query == null}）时给默认分页。
     *
     * <p>⭐ <b>为什么要有这个静态入口</b>：20 多个列表接口原来各自写
     * {@code query == null ? PageRequest.of(1, 20) : query.toPageRequest()}，
     * 默认页大小散落在 controller 里——改一次默认值要改 20 处，漏一处就是「某个列表
     * 默认 20 条、另一个默认 10 条」这种只在体感上被发现的偏差。
     * 现在默认值的唯一来源仍是 {@link PageRequest#of(Integer, Integer)}（null → 1 / 20），
     * 这里只负责把 null 查询体折进去。</p>
     */
    public static PageRequest toPageRequest(PageQuery query) {
        return query == null ? PageRequest.of(null, null) : query.toPageRequest();
    }

    /**
     * 空查询：所有筛选项为 null，仅供「请求体未传」时占位。
     *
     * <p>替掉 controller 里 {@code new PageQuery(null, null, ...)} 那种 15 个 null 的构造——
     * 多一个字段就要改所有调用点，而这里只需改一处。</p>
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
