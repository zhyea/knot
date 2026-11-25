package org.chobit.knot.svc.vo.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表视图：只承载用户列表列 + 定位标识。
 * 编辑表单要用的 {@code roleIds} / {@code deptCode} / 引用数只在详情端点下发（铁律 12）。
 */
public record UserListItem(Long id,
                           String username,
                           String realName,
                           String deptName,
                           List<String> roleNames,
                           Integer status,
                           LocalDateTime lastLoginTime,
                           LocalDateTime updatedAt) {
}
