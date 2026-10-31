package org.chobit.knot.svc.vo.auth;

/**
 * 管理面「菜单」载体（授权管理 → 菜单维护）。
 *
 * <p>⚠ <b>与 {@link AdminMenuItem} 不是一回事</b>：那个是「当前登录用户的导航树」
 * （裁剪过、带 {@code children}、{@code moduleId} 而非 {@code id}），
 * 这个是维护界面的完整行。命名带 {@code Detail} 中缀就是为了不被误用——
 * 维护界面要能看到禁用菜单，导航树不能。</p>
 */
public record AdminMenuDetailItem(
        Long id,
        Long moduleId,
        String moduleCode,
        String moduleName,
        String moduleIcon,
        Integer moduleSortOrder,
        Long parentId,
        String menuCode,
        String menuName,
        String routePath,
        String componentKey,
        String icon,
        Integer sortOrder,
        /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
        Integer status
) {
}
