package org.chobit.knot.svc.vo.auth;

/**
 * 管理面「角色」载体（授权管理 → 角色维护）。
 *
 * <p>角色没有启用状态：角色的「能不能用」由绑定关系表达，不靠一个 status 列
 * （见 {@code ks_admin_roles} 表结构）。</p>
 */
public record AdminRoleItem(
        Long id,
        String code,
        String name,
        String description
) {
}
