package org.chobit.knot.dal.entity;

import lombok.Data;

@Data
public class AdminRoleEntity {
    private Long id;
    private String code;
    private String name;
    private String description;
}
