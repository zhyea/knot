package org.chobit.knot.svc.service;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.dal.entity.AdminPermissionEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.svc.converter.AuthorizationConverter;
import org.chobit.knot.svc.service.support.AuthorizationManageSupport;
import org.chobit.knot.svc.vo.auth.AdminPermissionItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Permission management service for authorization administration.
 *
 * <p>⭐ 对外承载 {@link AdminPermissionItem}；实体只在 svc 内部流转。</p>
 */
@Service
public class AuthorizationPermissionService {

    private final AuthorizationManageSupport support;
    private final AuthorizationConverter converter;

    /**
     * Constructs a new instance.
     */
    public AuthorizationPermissionService(AuthorizationManageSupport support,
                                          AuthorizationConverter converter) {
        this.support = support;
        this.converter = converter;
    }

    /**
     * Lists permissions.
     */
    public List<AdminPermissionItem> listPermissions(String keyword, Long moduleId, Long menuId, String permissionType) {
        return converter.toPermissionItemList(
                support.mapper().listPermissions(StringUtils.trimToNull(keyword), moduleId, menuId,
                        StringUtils.trimToNull(permissionType)));
    }

    /**
     * Creates a permission.
     */
    @Transactional
    public AdminPermissionItem createPermission(AdminPermissionItem request) {
        AdminPermissionEntity entity = converter.toPermissionEntity(request);
        support.validatePermission(entity, null);
        support.mapper().insertPermission(entity);
        // 主键由 insert 回填到 entity —— record 不可变，回头读 request.id() 仍是 null
        return converter.toPermissionItem(support.getPermissionById(entity.getId()));
    }

    /**
     * Updates a permission.
     */
    @Transactional
    public AdminPermissionItem updatePermission(Long id, AdminPermissionItem request) {
        AdminPermissionEntity existing = support.getPermissionById(id);
        AdminPermissionEntity entity = converter.toPermissionEntity(request);
        entity.setId(id);
        if (entity.getStatus() == null) {
            entity.setStatus(existing.getStatus());
        }
        support.validatePermission(entity, id);
        support.mapper().updatePermission(entity);
        return converter.toPermissionItem(support.getPermissionById(id));
    }

    /**
     * Updates permission enabled status only.
     */
    @Transactional
    public AdminPermissionItem updateStatus(Long id, boolean enabled) {
        support.getPermissionById(id);
        support.mapper().updatePermissionStatus(id, EnabledStatusEnum.codeOf(enabled));
        return converter.toPermissionItem(support.getPermissionById(id));
    }

    /**
     * Deletes a permission.
     */
    @Transactional
    public AdminPermissionItem deletePermission(Long id) {
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
        return converter.toPermissionItem(existing);
    }

    /**
     * Returns a permission snapshot for operation log auditing.
     *
     * <p>仅由 {@code @OperationLog} 的 SpEL 表达式（{@code @authorizationPermissionService.permissionAuditSnapshot(#p0)}）
     * 反射调用，Java 侧无直接引用，IDE 会误报 unused。</p>
     */
    @SuppressWarnings("unused")
    public AdminPermissionItem permissionAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        return converter.toPermissionItem(support.getPermissionById(id));
    }
}
