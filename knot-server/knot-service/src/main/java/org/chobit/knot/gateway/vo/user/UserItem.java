package org.chobit.knot.gateway.vo.user;

import java.time.LocalDateTime;

public record UserItem(Long id, String username, String password, String realName, Integer status, LocalDateTime lastLoginTime, LocalDateTime updatedAt) {
}
