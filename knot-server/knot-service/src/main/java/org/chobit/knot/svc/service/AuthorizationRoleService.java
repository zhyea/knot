package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.dal.entity.AdminRoleEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.converter.AuthorizationConverter;
import org.chobit.knot.svc.service.support.AuthorizationManageSupport;
import org.chobit.knot.svc.vo.auth.AdminAuthorizationSnapshotResponse;
import org.chobit.knot.svc.vo.auth.AdminRoleItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Role management service for authorization administration.
 *
 * <p>⭐ 分页查询不能直接 {@code converter.toRoleItemList(PageInfo.getList())}：PageHelper 的
 * {@code Page} 对象在 {@code PageResult.of} 里既取 {@code getTotal()} 又取列表，
 * 这里必须在<b>同一个 PageInfo</b> 上取 total，否则 total 会丢（列表只剩当前页条数）。</p>
 */
@Service
public class AuthorizationRoleService {

    private final AuthorizationManageSupport support;
    private final AuthorizationConverter converter;

    /**
     * Constructs a new instance.
     */
    public AuthorizationRoleService(AuthorizationManageSupport support,
                                    AuthorizationConverter converter) {
        this.support = support;
        this.converter = converter;
    }

    /**
     * Lists roles with pagination.
     */
    public PageResult<AdminRoleItem> listRoles(PageRequest pageRequest, String keyword) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<AdminRoleEntity> pageInfo = new PageInfo<>(support.mapper().listRoles(StringUtils.trimToNull(keyword)));
            return PageResult.of(converter.toRoleItemList(pageInfo.getList()), pageInfo.getTotal(),
                    pageRequest.pageNum(), pageRequest.pageSize());
        }
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
    public AdminRoleItem createRole(AdminRoleItem request) {
        AdminRoleEntity entity = converter.toRoleEntity(request);
        support.validateRole(entity, null);
        support.mapper().insertRole(entity);
        // 主键由 insert 回填到 entity —— record 不可变，回头读 request.id() 仍是 null
        return converter.toRoleItem(support.getRoleById(entity.getId()));
    }

    /**
     * Updates a role.
     */
    @Transactional
    public AdminRoleItem updateRole(Long id, AdminRoleItem request) {
        support.getRoleById(id);
        AdminRoleEntity entity = converter.toRoleEntity(request);
        entity.setId(id);
        support.validateRole(entity, id);
        support.mapper().updateRole(entity);
        return converter.toRoleItem(support.getRoleById(id));
    }

    /**
     * Deletes a role.
     */
    @Transactional
    public AdminRoleItem deleteRole(Long id) {
        AdminRoleEntity existing = support.getRoleById(id);
        Long usage = support.mapper().countUserRoles(id);
        if (usage != null && usage > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色已绑定用户，无法删除");
        }
        support.mapper().deleteRole(id);
        return converter.toRoleItem(existing);
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

    /**
     * Returns a role snapshot for operation log auditing.
     *
     * <p>仅由 {@code @OperationLog} 的 SpEL 表达式（{@code @authorizationRoleService.roleAuditSnapshot(#p0)}）
     * 反射调用，Java 侧无直接引用，IDE 会误报 unused。</p>
     */
    @SuppressWarnings("unused")
    public AdminRoleItem roleAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        return converter.toRoleItem(support.getRoleById(id));
    }
}
