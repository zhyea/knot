package org.chobit.knot.svc.vo.auth;

/**
 * 管理面「角色」载体（授权管理 → 角色维护）。
 * 角色没有启用状态，「能不能用」由绑定关系表达，不靠 status 列。
 */
public record AdminRoleItem(
        Long id,
        String code,
        String name,
        String description
) {
}
