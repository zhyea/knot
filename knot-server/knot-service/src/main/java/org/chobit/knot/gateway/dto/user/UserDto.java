package org.chobit.knot.gateway.dto.user;

import java.time.LocalDateTime;
import java.util.List;

public record UserDto(Long id, String username, String password, String realName, String deptCode, String deptName,
                      Integer status, List<Long> roleIds, List<String> roleNames,
                      LocalDateTime lastLoginTime, LocalDateTime updatedAt,
                      /** 只读派生：登录名的引用处数；0 表示未绑定，可改用户编码 */
                      Long boundCount) {
}
