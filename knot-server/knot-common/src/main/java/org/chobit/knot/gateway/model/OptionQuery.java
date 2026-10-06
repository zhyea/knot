package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 下拉候选（options）接口统一请求体。
 *
 * <p>约定见 {@code .workbuddy/memory/options-refactor-constraints.md} 第 0/1 节：
 * 有传参一律 POST /api/{resource}/options；仅完全无传参才 GET。
 * 资源专属过滤字段由各资源在自身 OptionQuery 中显式声明，禁止通用 Map 透传（R5）。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OptionQuery(
        Integer pageNum,
        Integer pageSize,
        String keyword,
        /** 已选值回显（上限 100，超额由 Service 拒绝） */
        List<String> values,
        /** 仅返回启用项，默认 true */
        Boolean enabledOnly,
        /** 含逻辑删除项，默认 false */
        Boolean includeDeleted
) {
    /** 服务端分页上限，超额钳制 */
    public static final int MAX_PAGE_SIZE = 50;
    /** values 上限，超额拒绝 */
    public static final int MAX_VALUES = 100;

    public int effectivePageNum() {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    public int effectivePageSize() {
        if (pageSize == null || pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public boolean effectiveEnabledOnly() {
        return enabledOnly == null || enabledOnly;
    }

    public boolean effectiveIncludeDeleted() {
        return includeDeleted != null && includeDeleted;
    }
}
