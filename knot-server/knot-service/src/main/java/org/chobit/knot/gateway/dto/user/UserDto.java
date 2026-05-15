package org.chobit.knot.gateway.dto.user;

import java.time.LocalDateTime;

public record UserDto(Long id, String username, String password, String realName, Integer status, LocalDateTime lastLoginTime, LocalDateTime updatedAt) {
}
