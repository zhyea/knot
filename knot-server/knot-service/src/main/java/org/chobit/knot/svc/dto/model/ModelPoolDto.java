package org.chobit.knot.svc.dto.model;

import org.chobit.knot.gateway.model.StickyPolicy;

import java.util.List;

public record ModelPoolDto(
        Long id,
        String poolCode,
        String name,
        String logicalModelCode,
        String logicalModelName,
        String modelType,
        String selectionStrategy,
        /** 池内粘性路由策略；null 表示未配置，运行时按内置默认（关闭）处理 */
        StickyPolicy stickyPolicy,
        boolean enabled,
        /** 已逻辑删除：管理列表仍展示（浅红底 + 恢复按钮），下拉/路由解析一律排除 */
        boolean deleted,
        String remark,
        List<ModelPoolItemDto> items,
        /**
         * 只读派生：模型池编码的引用处数（池内模型条目 + 路由规则目标）。
         * 未绑定（0）时编码可改，>0 时后端拒绝改码、前端置灰输入框。
         */
        Long boundCount
) {
}
