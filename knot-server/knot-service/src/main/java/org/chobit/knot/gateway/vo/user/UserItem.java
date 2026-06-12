package org.chobit.knot.gateway.vo.user;

import java.time.LocalDateTime;
import java.util.List;

public record UserItem(Long id, String username, String password, String realName, Long deptId, String deptName,
                       Integer status, List<Long> roleIds, List<String> roleNames,
                       LocalDateTime lastLoginTime, LocalDateTime updatedAt) {
}
