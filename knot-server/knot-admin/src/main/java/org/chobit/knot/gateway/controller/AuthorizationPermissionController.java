package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.entity.AdminPermissionEntity;
import org.chobit.knot.gateway.service.AuthorizationPermissionService;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Permission endpoints for authorization management.
 */
@RestController
@RequestMapping("/api/system/authorizations/permissions")
@AuthCheck
public class AuthorizationPermissionController {

    private final AuthorizationPermissionService permissionService;

    /**
     * Constructs a new instance.
     */
    public AuthorizationPermissionController(AuthorizationPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * Lists permissions.
     */
    @GetMapping
    public List<AdminPermissionEntity> listPermissions(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long moduleId,
                                                       @RequestParam(required = false) Long menuId,
                                                       @RequestParam(required = false) String permissionType) {
        return permissionService.listPermissions(keyword, moduleId, menuId, permissionType);
    }

    /**
     * Creates a permission.
     */
    @PostMapping
    public AdminPermissionEntity createPermission(@RequestBody AdminPermissionEntity request) {
        return permissionService.createPermission(request);
    }

    /**
     * Updates a permission.
     */
    @PutMapping("/{id}")
    public AdminPermissionEntity updatePermission(@PathVariable Long id, @RequestBody AdminPermissionEntity request) {
        return permissionService.updatePermission(id, request);
    }

    /**
     * Updates permission enabled status.
     */
    @PutMapping("/{id}/status")
    public AdminPermissionEntity updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        return permissionService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
    }

    /**
     * Deletes a permission.
     */
    @DeleteMapping("/{id}")
    public AdminPermissionEntity deletePermission(@PathVariable Long id) {
        return permissionService.deletePermission(id);
    }
}
