package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.entity.AdminMenuEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Menu management service for authorization administration.
 */
@Service
public class AuthorizationMenuService {

    private final AuthorizationManageSupport support;

    /**
     * Constructs a new instance.
     */
    public AuthorizationMenuService(AuthorizationManageSupport support) {
        this.support = support;
    }

    /**
     * Lists menus.
     */
    public List<AdminMenuEntity> listMenus(String keyword, Long moduleId, Long parentId) {
        return support.mapper().listMenus(support.normalizeKeyword(keyword), moduleId, parentId);
    }

    /**
     * Creates a menu.
     */
    @Transactional
    public AdminMenuEntity createMenu(AdminMenuEntity request) {
        support.validateMenu(request, null);
        support.mapper().insertMenu(request);
        return support.getMenuById(request.getId());
    }

    /**
     * Updates a menu.
     */
    @Transactional
    public AdminMenuEntity updateMenu(Long id, AdminMenuEntity request) {
        AdminMenuEntity existing = support.getMenuById(id);
        request.setId(id);
        if (request.getStatus() == null) {
            request.setStatus(existing.getStatus());
        }
        support.validateMenu(request, id);
        support.mapper().updateMenu(request);
        return support.getMenuById(id);
    }

    /**
     * Updates menu enabled status only.
     */
    @Transactional
    public AdminMenuEntity updateStatus(Long id, boolean enabled) {
        support.getMenuById(id);
        support.mapper().updateMenuStatus(id, EnabledStatusEnum.codeOf(enabled));
        return support.getMenuById(id);
    }

    /**
     * Deletes a menu.
     */
    @Transactional
    public AdminMenuEntity deleteMenu(Long id) {
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
        return existing;
    }

    /**
     * Returns a menu snapshot for operation log auditing.
     *
     * <p>仅由 {@code @OperationLog} 的 SpEL 表达式（{@code @authorizationMenuService.menuAuditSnapshot(#p0)}）
     * 反射调用，Java 侧无直接引用，IDE 会误报 unused。
     */
    @SuppressWarnings("unused")
    public AdminMenuEntity menuAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        return support.getMenuById(id);
    }
}
