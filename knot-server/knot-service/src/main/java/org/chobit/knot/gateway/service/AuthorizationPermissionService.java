package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.entity.AdminPermissionEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Permission management service for authorization administration.
 */
@Service
public class AuthorizationPermissionService {

    private final AuthorizationManageSupport support;

    /**
     * Constructs a new instance.
     */
    public AuthorizationPermissionService(AuthorizationManageSupport support) {
        this.support = support;
    }

    /**
     * Lists permissions.
     */
    public List<AdminPermissionEntity> listPermissions(String keyword, Long moduleId, Long menuId, String permissionType) {
        return support.mapper().listPermissions(support.normalizeKeyword(keyword), moduleId, menuId, support.normalizeKeyword(permissionType));
    }

    /**
     * Creates a permission.
     */
    @Transactional
    public AdminPermissionEntity createPermission(AdminPermissionEntity request) {
        support.validatePermission(request, null);
        support.mapper().insertPermission(request);
        return support.getPermissionById(request.getId());
    }

    /**
     * Updates a permission.
     */
    @Transactional
    public AdminPermissionEntity updatePermission(Long id, AdminPermissionEntity request) {
        AdminPermissionEntity existing = support.getPermissionById(id);
        request.setId(id);
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            request.setStatus(existing.getStatus());
        }
        support.validatePermission(request, id);
        support.mapper().updatePermission(request);
        return support.getPermissionById(id);
    }

    /**
     * Updates permission enabled status only.
     */
    @Transactional
    public AdminPermissionEntity updateStatus(Long id, boolean enabled) {
        support.getPermissionById(id);
        support.mapper().updatePermissionStatus(id, enabled ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
        return support.getPermissionById(id);
    }

    /**
     * Deletes a permission.
     */
    @Transactional
    public AdminPermissionEntity deletePermission(Long id) {
        AdminPermissionEntity existing = support.getPermissionById(id);
        if (existing.getBuiltIn() != null && existing.getBuiltIn() == 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统内置权限不允许删除");
        }
        Long roleUsage = support.mapper().countRolePermissionUsage(id);
        if (roleUsage != null && roleUsage > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "权限已绑定角色，无法删除");
        }
        Long bindingUsage = support.mapper().countApiBindingUsage(id);
        if (bindingUsage != null && bindingUsage > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "权限已绑定接口，无法删除");
        }
        support.mapper().deletePermission(id);
        return existing;
    }
}
