package org.chobit.knot.gateway.dto.model;

import java.util.List;
import java.time.LocalDateTime;

public record LogicalModelDto(
        Long id,
        String modelCode,
        String modelName,
        String modelType,
        String modelFamily,
        String description,
        List<String> tags,
        List<String> useCases,
        Integer contextWindow,
        Integer maxOutputTokens,
        List<String> inputModalities,
        List<String> outputModalities,
        List<String> languages,
        String visibility,
        /** 发布状态（LogicalModelPublishStatusEnum）：1-DRAFT 2-PUBLISHED 3-ARCHIVED */
        Integer publishStatus,
        boolean enabled,
        /** 已逻辑删除：管理列表仍展示（浅红底 + 恢复按钮），下拉/绑定类查询一律排除 */
        boolean deleted,
        Integer sortOrder,
        boolean featured,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ProviderModelMappingDto> mappings,
        /**
         * 只读派生：本统一模型编码被外部按业务码引用的总处数（模型池 / 供应商模型映射 /
         * 路由规则 / 调试预设）。未绑定（0）时编码可改，>0 时后端拒绝改码、前端置灰输入框。
         */
        Long boundCount
) {
}
