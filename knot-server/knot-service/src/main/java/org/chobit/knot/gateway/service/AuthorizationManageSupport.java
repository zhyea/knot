package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.AdminApiPermissionBindingEntity;
import org.chobit.knot.gateway.entity.AdminMenuEntity;
import org.chobit.knot.gateway.entity.AdminModuleEntity;
import org.chobit.knot.gateway.entity.AdminPermissionEntity;
import org.chobit.knot.gateway.entity.AdminRoleEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.AdminAuthorizationManageMapper;
import org.springframework.stereotype.Component;

/**
 * Shared validation and lookup support for authorization management services.
 */
@Component
public class AuthorizationManageSupport {

    private final AdminAuthorizationManageMapper manageMapper;

    /**
     * Constructs a new instance.
     */
    public AuthorizationManageSupport(AdminAuthorizationManageMapper manageMapper) {
        this.manageMapper = manageMapper;
    }

    /**
     * Returns the mapper used by authorization management services.
     */
    public AdminAuthorizationManageMapper mapper() {
        return manageMapper;
    }

    /**
     * Returns the role by id.
     */
    public AdminRoleEntity getRoleById(Long id) {
        AdminRoleEntity entity = manageMapper.getRoleById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return entity;
    }

    /**
     * Returns the module by id.
     */
    public AdminModuleEntity getModuleById(Long id) {
        AdminModuleEntity entity = manageMapper.getModuleById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模块不存在");
        }
        return entity;
    }

    /**
     * Returns the menu by id.
     */
    public AdminMenuEntity getMenuById(Long id) {
        AdminMenuEntity entity = manageMapper.getMenuById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "菜单不存在");
        }
        return entity;
    }

    /**
     * Returns the permission by id.
     */
    public AdminPermissionEntity getPermissionById(Long id) {
        AdminPermissionEntity entity = manageMapper.getPermissionById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "权限不存在");
        }
        return entity;
    }

    /**
     * Returns the api permission binding by id.
     */
    public AdminApiPermissionBindingEntity getApiPermissionBindingById(Long id) {
        AdminApiPermissionBindingEntity entity = manageMapper.getApiPermissionBindingById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "接口权限绑定不存在");
        }
        return entity;
    }

    /**
     * Validates role fields.
     */
    public void validateRole(AdminRoleEntity request, Long excludeId) {
        if (request == null || isBlank(request.getCode()) || isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色编码和名称不能为空");
        }
        Long count = manageMapper.countRoleByCode(request.getCode().trim(), excludeId);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码已存在");
        }
        request.setCode(request.getCode().trim());
        request.setName(request.getName().trim());
    }

    /**
     * Validates module fields.
     */
    public void validateModule(AdminModuleEntity request, Long excludeId) {
        if (request == null || isBlank(request.getModuleCode()) || isBlank(request.getModuleName())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模块编码和名称不能为空");
        }
        Long count = manageMapper.countModuleByCode(request.getModuleCode().trim(), excludeId);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "模块编码已存在");
        }
        request.setModuleCode(request.getModuleCode().trim());
        request.setModuleName(request.getModuleName().trim());
        if (request.getSortOrder() == null) {
            request.setSortOrder(0);
        }
        if (isBlank(request.getStatus())) {
            request.setStatus("ENABLED");
        }
    }

    /**
     * Validates menu fields.
     */
    public void validateMenu(AdminMenuEntity request, Long excludeId) {
        if (request == null || request.getModuleId() == null || isBlank(request.getMenuCode()) || isBlank(request.getMenuName())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "菜单所属模块、编码和名称不能为空");
        }
        getModuleById(request.getModuleId());
        if (request.getParentId() != null) {
            if (excludeId != null && request.getParentId().equals(excludeId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上级菜单不能选择自身");
            }
            getMenuById(request.getParentId());
        }
        Long count = manageMapper.countMenuByCode(request.getMenuCode().trim(), excludeId);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "菜单编码已存在");
        }
        request.setMenuCode(request.getMenuCode().trim());
        request.setMenuName(request.getMenuName().trim());
        if (request.getRoutePath() != null) {
            request.setRoutePath(request.getRoutePath().trim());
        }
        if (request.getComponentKey() != null) {
            request.setComponentKey(request.getComponentKey().trim());
        }
        if (request.getSortOrder() == null) {
            request.setSortOrder(0);
        }
        if (isBlank(request.getStatus())) {
            request.setStatus("ENABLED");
        }
    }

    /**
     * Validates permission fields.
     */
    public void validatePermission(AdminPermissionEntity request, Long excludeId) {
        if (request == null || request.getModuleId() == null || isBlank(request.getPermissionCode())
                || isBlank(request.getPermissionName()) || isBlank(request.getPermissionType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限模块、编码、名称和类型不能为空");
        }
        getModuleById(request.getModuleId());
        if (request.getMenuId() != null) {
            getMenuById(request.getMenuId());
        }
        Long count = manageMapper.countPermissionByCode(request.getPermissionCode().trim(), excludeId);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "权限编码已存在");
        }
        request.setPermissionCode(request.getPermissionCode().trim());
        request.setPermissionName(request.getPermissionName().trim());
        request.setPermissionType(request.getPermissionType().trim());
        if (request.getBuiltIn() == null) {
            request.setBuiltIn(0);
        }
        if (isBlank(request.getStatus())) {
            request.setStatus("ENABLED");
        }
    }

    /**
     * Validates api permission binding fields.
     */
    public void validateApiPermissionBinding(AdminApiPermissionBindingEntity request, Long excludeId) {
        if (request == null || request.getPermissionId() == null || isBlank(request.getHttpMethod()) || isBlank(request.getPathPattern())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限、HTTP 方法和路径不能为空");
        }
        getPermissionById(request.getPermissionId());
        String httpMethod = request.getHttpMethod().trim().toUpperCase();
        String pathPattern = request.getPathPattern().trim();
        Long count = manageMapper.countApiPermissionBinding(httpMethod, pathPattern, excludeId);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "接口权限绑定已存在");
        }
        request.setHttpMethod(httpMethod);
        request.setPathPattern(pathPattern);
        if (request.getControllerClass() != null) {
            request.setControllerClass(request.getControllerClass().trim());
        }
        if (isBlank(request.getStatus())) {
            request.setStatus("ENABLED");
        }
    }

    /**
     * Ensures that the module is not referenced by menus or permissions.
     */
    public void ensureModuleUnused(Long moduleId) {
        Long menuCount = manageMapper.countMenusByModuleId(moduleId);
        if (menuCount != null && menuCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "模块下仍有菜单，无法删除");
        }
        Long permissionCount = manageMapper.countPermissionsByModuleId(moduleId);
        if (permissionCount != null && permissionCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "模块下仍有关联权限，无法删除");
        }
    }

    /**
     * Normalizes a keyword value for fuzzy query usage.
     */
    public String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String value = keyword.trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
