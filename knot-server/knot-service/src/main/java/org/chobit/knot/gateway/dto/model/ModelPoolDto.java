package org.chobit.knot.gateway.dto.model;

import java.util.List;

public record ModelPoolDto(
        Long id,
        String poolCode,
        String name,
        String logicalModelCode,
        String logicalModelName,
        String modelType,
        String selectionStrategy,
        boolean enabled,
        /** 已逻辑删除：管理列表仍展示（浅红底 + 恢复按钮），下拉/路由解析一律排除 */
        boolean deleted,
        String remark,
        List<ModelPoolItemDto> items
) {
}
