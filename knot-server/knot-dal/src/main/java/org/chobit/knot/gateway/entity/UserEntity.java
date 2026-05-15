package org.chobit.knot.gateway.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserEntity {
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime updatedAt;
}
