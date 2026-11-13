package org.chobit.knot.svc.vo.auth;

/**
 * 管理面「API 权限绑定」载体（授权管理 → Api 绑定）。
 * 字段与 {@code AdminApiPermissionBindingEntity} 一一对应；加减字段要先过本 record，实体加列不会自动变成 API 变更。
 * 同时用作入参，不能只读。
 */
public record AdminApiBindingItem(
        Long id,
        Long permissionId,
        String permissionCode,
        String permissionName,
        String httpMethod,
        String pathPattern,
        String controllerClass,
        /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
        Integer status
) {
}
