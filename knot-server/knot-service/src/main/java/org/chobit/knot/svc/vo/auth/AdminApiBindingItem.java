package org.chobit.knot.svc.vo.auth;

/**
 * 管理面「API 权限绑定」载体（授权管理 → Api 绑定）。
 *
 * <p>字段与 {@code AdminApiPermissionBindingEntity} 一一对应，<b>是刻意的</b>：
 * 这次迁移只把「层边界」立起来，不改对外 JSON 契约（前端零改动）。
 * 与实体的差别在于<b>谁说了算</b>——从这里往后，加减字段要先过本 record，
 * 实体加列不会自动变成 API 变更。</p>
 *
 * <p>⚠ 同时用作入参（同 {@code DepartmentItem} 先例），所以不能只读。</p>
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
