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
        /** 只读派生：机构编码被用户 / 应用引用的处数；0 表示未绑定，可改机构编码 */
        Long boundCount,
        List<DepartmentItem> children
) {
}
