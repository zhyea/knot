package org.chobit.knot.svc.dto.system;

import java.time.LocalDateTime;

public record DepartmentDto(
        Long id,
        String deptCode,
        String deptName,
        Long parentId,
        Integer status,
        Integer sortOrder,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 只读派生：机构编码被用户 / 应用引用的处数；0 表示未绑定，可改机构编码 */
        Long boundCount
) {
}
