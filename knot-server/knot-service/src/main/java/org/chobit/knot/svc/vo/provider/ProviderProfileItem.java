package org.chobit.knot.svc.vo.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ProviderProfileItem(
        Long id,
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 100) String name,
        // 供应商分类：可多选，多个用英文逗号分隔（如 "原厂,代理"），允许手工输入自定义项
        @NotBlank @Size(max = 255) String tag,
        @Size(max = 255) String remark,
        /**
         * 只读派生：按本编码绑定的供应商账户数。未绑定（0）时编码可改，>0 时后端拒绝改码、
         * 前端置灰输入框。
         */
        Long boundCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
