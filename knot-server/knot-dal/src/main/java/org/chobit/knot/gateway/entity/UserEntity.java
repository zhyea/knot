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
    /** 归属部门业务码（绑定 ks_departments.dept_code，非主键 id） */
    private String deptCode;
    private String deptName;
    private Integer status;
    private List<Long> roleIds;
    private List<String> roleNames;
    private LocalDateTime lastLoginTime;
    private LocalDateTime updatedAt;
    /** 只读派生：按 username 绑定该用户的应用 / 消费者 / 路由规则处数；0 表示可改登录名 */
    private Long boundCount;
}
