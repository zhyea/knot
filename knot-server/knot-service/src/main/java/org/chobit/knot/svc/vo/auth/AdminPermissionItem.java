package org.chobit.knot.svc.vo.auth;

/**
 * 管理面「权限项」载体（授权管理 → 权限维护）。
 *
 * <p>{@code builtIn} 为内置标记：内置权限不可删除（判据在 svc 的 validate 里，不在本载体）。</p>
 */
public record AdminPermissionItem(
        Long id,
        String permissionCode,
        String permissionName,
        String permissionType,
        Long moduleId,
        String moduleCode,
        String moduleName,
        Long menuId,
        String menuCode,
        String menuName,
        /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
        Integer status,
        Integer builtIn,
        String remark
) {
}
