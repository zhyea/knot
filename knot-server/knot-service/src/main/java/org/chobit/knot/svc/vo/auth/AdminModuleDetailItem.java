package org.chobit.knot.svc.vo.auth;

/**
 * 管理面「模块」载体（授权管理 → 模块维护）。
 *
 * <p>⚠ <b>与 {@link AdminModuleItem} 不是一回事</b>：那个带 {@code menus} 树、字段名是
 * {@code moduleId}，供布局渲染；这个是维护界面的行，主键就叫 {@code id}。</p>
 */
public record AdminModuleDetailItem(
        Long id,
        String moduleCode,
        String moduleName,
        String icon,
        Integer sortOrder,
        /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
        Integer status
) {
}
