package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AdminPermissionEntity {
    private Long id;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private Long moduleId;
    private Long menuId;
    private String status;
    private Integer builtIn;
    private String remark;
}
