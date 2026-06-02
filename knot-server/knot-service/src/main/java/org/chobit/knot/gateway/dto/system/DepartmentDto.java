package org.chobit.knot.gateway.dto.system;

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
        LocalDateTime updatedAt
) {
}
