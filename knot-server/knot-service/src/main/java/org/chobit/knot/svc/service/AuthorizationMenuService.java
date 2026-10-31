package org.chobit.knot.svc.service;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.dal.entity.AdminMenuEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.svc.converter.AuthorizationConverter;
import org.chobit.knot.svc.service.support.AuthorizationManageSupport;
import org.chobit.knot.svc.vo.auth.AdminMenuDetailItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Menu management service for authorization administration.
 *
 * <p>⭐ 对外载体是 {@link AdminMenuDetailItem}，不是导航树用的 {@code AdminMenuItem}：
 * 维护界面必须看到禁用菜单，导航树不能。两者字段集不同（前者主键 {@code id}、无 children），
 * 混用会让「关掉的菜单还出现在侧边栏」这种 bug 复活。</p>
 */
@Service
public class AuthorizationMenuService {

    private final AuthorizationManageSupport support;
    private final AuthorizationConverter converter;

    /**
     * Constructs a new instance.
     */
    public AuthorizationMenuService(AuthorizationManageSupport support,
                                    AuthorizationConverter converter) {
        this.support = support;
        this.converter = converter;
    }

    /**
     * Lists menus.
     */
    public List<AdminMenuDetailItem> listMenus(String keyword, Long moduleId, Long parentId) {
        return converter.toMenuItemList(
                support.mapper().listMenus(StringUtils.trimToNull(keyword), moduleId, parentId));
    }

    /**
     * Creates a menu.
     */
    @Transactional
    public AdminMenuDetailItem createMenu(AdminMenuDetailItem request) {
        AdminMenuEntity entity = converter.toMenuEntity(request);
        support.validateMenu(entity, null);
        support.mapper().insertMenu(entity);
        // 主键由 insert 回填到 entity —— record 不可变，回头读 request.id() 仍是 null
        return converter.toMenuItem(support.getMenuById(entity.getId()));
    }

    /**
     * Updates a menu.
     */
    @Transactional
    public AdminMenuDetailItem updateMenu(Long id, AdminMenuDetailItem request) {
        AdminMenuEntity existing = support.getMenuById(id);
        AdminMenuEntity entity = converter.toMenuEntity(request);
        entity.setId(id);
        if (entity.getStatus() == null) {
            entity.setStatus(existing.getStatus());
        }
        support.validateMenu(entity, id);
        support.mapper().updateMenu(entity);
        return converter.toMenuItem(support.getMenuById(id));
    }

    /**
     * Updates menu enabled status only.
     */
    @Transactional
    public AdminMenuDetailItem updateStatus(Long id, boolean enabled) {
        support.getMenuById(id);
        support.mapper().updateMenuStatus(id, EnabledStatusEnum.codeOf(enabled));
        return converter.toMenuItem(support.getMenuById(id));
    }

    /**
     * Deletes a menu.
     */
    @Transactional
    public AdminMenuDetailItem deleteMenu(Long id) {
        AdminMenuEntity existing = support.getMenuById(id);
        Long childCount = support.mapper().countChildMenus(id);
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "菜单存在子菜单，无法删除");
        }
        Long permissionCount = support.mapper().countPermissionsByMenuId(id);
        if (permissionCount != null && permissionCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "菜单已关联权限，无法删除");
        }
        support.mapper().deleteMenu(id);
        return converter.toMenuItem(existing);
    }

    /**
     * Returns a menu snapshot for operation log auditing.
     *
     * <p>仅由 {@code @OperationLog} 的 SpEL 表达式（{@code @authorizationMenuService.menuAuditSnapshot(#p0)}）
     * 反射调用，Java 侧无直接引用，IDE 会误报 unused。</p>
     */
    @SuppressWarnings("unused")
    public AdminMenuDetailItem menuAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        return converter.toMenuItem(support.getMenuById(id));
    }
}
