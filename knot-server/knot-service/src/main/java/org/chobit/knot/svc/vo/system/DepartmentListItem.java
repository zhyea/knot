package org.chobit.knot.svc.vo.system;

import java.time.LocalDateTime;

/**
 * 部门分页列表视图：只承载列表列与定位标识。
 * 层级关系由独立的 {@code /tree} 端点提供，备注 / 排序 / 绑定引用数只在详情端点下发（铁律 12）。
 */
public record DepartmentListItem(Long id,
                                 String deptCode,
                                 String deptName,
                                 Integer status,
                                 LocalDateTime updatedAt) {
}
