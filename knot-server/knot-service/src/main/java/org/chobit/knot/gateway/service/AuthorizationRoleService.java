package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.entity.AdminRoleEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.vo.auth.AdminAuthorizationSnapshotResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Role management service for authorization administration.
 */
@Service
public class AuthorizationRoleService {

    private final AuthorizationManageSupport support;

    /**
     * Constructs a new instance.
     */
    public AuthorizationRoleService(AuthorizationManageSupport support) {
        this.support = support;
    }

    /**
     * Lists roles with pagination.
     */
    public PageResult<AdminRoleEntity> listRoles(PageRequest pageRequest, String keyword) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<AdminRoleEntity> pageInfo = new PageInfo<>(support.mapper().listRoles(support.normalizeKeyword(keyword)));
        return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    /**
     * Returns the authorization snapshot of the specified role.
     */
    public AdminAuthorizationSnapshotResponse getRoleAuthorizationSnapshot(Long roleId) {
        AdminRoleEntity role = support.getRoleById(roleId);
        return new AdminAuthorizationSnapshotResponse(
                List.of(new AdminAuthorizationSnapshotResponse.AdminRoleSummary(role.getId(), role.getCode(), role.getName())),
                support.mapper().listRolePermissionIds(roleId)
        );
    }

    /**
     * Creates a role.
     */
    @Transactional
    public AdminRoleEntity createRole(AdminRoleEntity request) {
        support.validateRole(request, null);
        support.mapper().insertRole(request);
        return support.getRoleById(request.getId());
    }

    /**
     * Updates a role.
     */
    @Transactional
    public AdminRoleEntity updateRole(Long id, AdminRoleEntity request) {
        support.getRoleById(id);
        request.setId(id);
        support.validateRole(request, id);
        support.mapper().updateRole(request);
        return support.getRoleById(id);
    }

    /**
     * Deletes a role.
     */
    @Transactional
    public AdminRoleEntity deleteRole(Long id) {
        AdminRoleEntity existing = support.getRoleById(id);
        Long usage = support.mapper().countUserRoles(id);
        if (usage != null && usage > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色已绑定用户，无法删除");
        }
        support.mapper().deleteRole(id);
        return existing;
    }

    /**
     * Replaces the permission bindings of the specified role.
     */
    @Transactional
    public AdminAuthorizationSnapshotResponse saveRolePermissions(Long roleId, List<Long> permissionIds) {
        support.getRoleById(roleId);
        support.mapper().deleteRolePermissions(roleId);
        List<Long> normalizedIds = permissionIds == null ? List.of() : permissionIds.stream().distinct().toList();
        if (!normalizedIds.isEmpty()) {
            support.mapper().insertRolePermissions(roleId, normalizedIds);
        }
        return getRoleAuthorizationSnapshot(roleId);
    }
}
