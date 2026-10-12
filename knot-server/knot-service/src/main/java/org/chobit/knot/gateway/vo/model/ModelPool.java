package org.chobit.knot.gateway.vo.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.chobit.knot.gateway.model.StickyPolicy;

import java.util.List;

public record ModelPool(
        Long id,
        @NotBlank @Size(max = 64) String poolCode,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 128) String logicalModelCode,
        @Size(max = 128) String logicalModelName,
        @Size(max = 64) String modelType,
        @NotBlank @Size(max = 32) String selectionStrategy,
        /** 池内粘性路由策略；null 表示未启用（默认关闭） */
        StickyPolicy stickyPolicy,
        boolean enabled,
        boolean deleted,
        @Size(max = 255) String remark,
        @Valid List<ModelPoolItem> items
) {
}
