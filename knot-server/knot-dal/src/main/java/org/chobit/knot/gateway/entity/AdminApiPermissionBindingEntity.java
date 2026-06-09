package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AdminApiPermissionBindingEntity {
    private Long id;
    private Long permissionId;
    private String permissionCode;
    private String httpMethod;
    private String pathPattern;
    private String controllerClass;
    private String handlerMethod;
    private String status;
}
