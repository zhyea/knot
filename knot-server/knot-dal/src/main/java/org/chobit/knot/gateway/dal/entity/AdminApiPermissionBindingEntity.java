package org.chobit.knot.gateway.dal.entity;

import lombok.Data;

@Data
public class AdminApiPermissionBindingEntity {
    private Long id;
    private Long permissionId;
    private String permissionCode;
    private String permissionName;
    private String httpMethod;
    private String pathPattern;
    private String controllerClass;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
