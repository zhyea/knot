package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.entity.AdminRoleEntity;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.model.RoleOptionQuery;
import org.chobit.knot.gateway.service.AuthorizationRoleService;
import org.chobit.knot.gateway.service.OptionsService;
import org.chobit.knot.gateway.vo.auth.AdminAuthorizationSnapshotResponse;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.OptionPage;
import org.chobit.knot.gateway.vo.common.meta.RoleOptionMeta;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Role endpoints for authorization management.
 */
@RestController
@RequestMapping("/api/system/authorizations/roles")
@AuthCheck
public class AuthorizationRoleController {

    private final AuthorizationRoleService roleService;
    private final OptionsService optionsService;

    /**
     * Constructs a new instance.
     */
    public AuthorizationRoleController(AuthorizationRoleService roleService, OptionsService optionsService) {
        this.roleService = roleService;
        this.optionsService = optionsService;
    }

    /**
     * Lists roles.
     */
    @PostMapping("/list")
    public PageResult<AdminRoleEntity> listRoles(@RequestBody(required = false) PageQuery query) {
        PageRequest pageRequest = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        return roleService.listRoles(pageRequest, query == null ? null : query.keyword());
    }

    /**
     * 角色下拉候选（options）。value=id，meta=roleCode；{@code ks_roles} 无启用态，disabled 恒 0。
     */
    @PostMapping("/options")
    public OptionPage<OptionItem<RoleOptionMeta>> listOptions(@RequestBody(required = false) RoleOptionQuery query) {
        return optionsService.listRoleOptions(query);
    }

    /**
     * Returns role authorization snapshot.
     */
    @GetMapping("/{roleId}/snapshot")
    public AdminAuthorizationSnapshotResponse getRoleSnapshot(@PathVariable Long roleId) {
        return roleService.getRoleAuthorizationSnapshot(roleId);
    }

    /**
     * Creates a role.
     */
    @OperationLog(module = "authorization", operation = "CREATE", entityType = "Role",
            entityIdAfter = "#result.id",
            entityNameAfter = "#result.name",
            description = "'新建角色'",
            newValueSpel = "#result")
    @PostMapping
    public AdminRoleEntity createRole(@RequestBody AdminRoleEntity request) {
        return roleService.createRole(request);
    }

    /**
     * Updates a role.
     */
    @OperationLog(module = "authorization", operation = "UPDATE", entityType = "Role",
            entityId = "#p0",
            entityNameAfter = "#result.name",
            description = "'更新角色'",
            oldValueSpel = "@authorizationRoleService.roleAuditSnapshot(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{id}")
    public AdminRoleEntity updateRole(@PathVariable Long id, @RequestBody AdminRoleEntity request) {
        return roleService.updateRole(id, request);
    }

    /**
     * Deletes a role.
     */
    @OperationLog(module = "authorization", operation = "DELETE", entityType = "Role",
            entityId = "#p0",
            entityNameAfter = "#result.name",
            description = "'删除角色'",
            oldValueSpel = "@authorizationRoleService.roleAuditSnapshot(#p0)",
            recordNewValue = false)
    @DeleteMapping("/{id}")
    public AdminRoleEntity deleteRole(@PathVariable Long id) {
        return roleService.deleteRole(id);
    }

    /**
     * Updates role permission bindings.
     */
    @OperationLog(module = "authorization", operation = "GRANT", entityType = "RolePermission",
            entityId = "#p0",
            description = "'保存角色权限授权'",
            oldValueSpel = "@authorizationRoleService.roleAuditSnapshot(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{roleId}/permissions")
    public AdminAuthorizationSnapshotResponse saveRolePermissions(@PathVariable Long roleId,
                                                                  @RequestBody List<Long> permissionIds) {
        return roleService.saveRolePermissions(roleId, permissionIds);
    }
}
