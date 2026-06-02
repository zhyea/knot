package org.chobit.knot.gateway.vo.system;

import java.util.List;
import java.time.LocalDateTime;

public record DepartmentItem(
        Long id,
        String deptCode,
        String deptName,
        Long parentId,
        Integer status,
        Integer sortOrder,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<DepartmentItem> children
) {
}
