package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class UserEntity {
    private Long id;
    private String username;
    private String realName;
    private String status;
}
