package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.entity.AdminRoleEntity;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.AuthorizationRoleService;
import org.chobit.knot.gateway.vo.auth.AdminAuthorizationSnapshotResponse;
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

    /**
     * Constructs a new instance.
     */
    public AuthorizationRoleController(AuthorizationRoleService roleService) {
        this.roleService = roleService;
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
     * Returns role authorization snapshot.
     */
    @GetMapping("/{roleId}/snapshot")
    public AdminAuthorizationSnapshotResponse getRoleSnapshot(@PathVariable Long roleId) {
        return roleService.getRoleAuthorizationSnapshot(roleId);
    }

    /**
     * Creates a role.
     */
    @PostMapping
    public AdminRoleEntity createRole(@RequestBody AdminRoleEntity request) {
        return roleService.createRole(request);
    }

    /**
     * Updates a role.
     */
    @PutMapping("/{id}")
    public AdminRoleEntity updateRole(@PathVariable Long id, @RequestBody AdminRoleEntity request) {
        return roleService.updateRole(id, request);
    }

    /**
     * Deletes a role.
     */
    @DeleteMapping("/{id}")
    public AdminRoleEntity deleteRole(@PathVariable Long id) {
        return roleService.deleteRole(id);
    }

    /**
     * Updates role permission bindings.
     */
    @PutMapping("/{roleId}/permissions")
    public AdminAuthorizationSnapshotResponse saveRolePermissions(@PathVariable Long roleId,
                                                                  @RequestBody List<Long> permissionIds) {
        return roleService.saveRolePermissions(roleId, permissionIds);
    }
}
