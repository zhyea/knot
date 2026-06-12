package org.chobit.knot.gateway.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserEntity {
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private Long deptId;
    private String deptName;
    private Integer status;
    private List<Long> roleIds;
    private List<String> roleNames;
    private LocalDateTime lastLoginTime;
    private LocalDateTime updatedAt;
}
