package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.entity.AdminApiPermissionBindingEntity;
import org.chobit.knot.gateway.service.AuthorizationApiBindingService;
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
 * Api binding endpoints for authorization management.
 */
@RestController
@RequestMapping("/api/system/authorizations/api-bindings")
@AuthCheck
public class AuthorizationApiBindingController {

    private final AuthorizationApiBindingService apiBindingService;

    /**
     * Constructs a new instance.
     */
    public AuthorizationApiBindingController(AuthorizationApiBindingService apiBindingService) {
        this.apiBindingService = apiBindingService;
    }

    /**
     * Lists api permission bindings.
     */
    @GetMapping
    public List<AdminApiPermissionBindingEntity> listApiBindings(@RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String httpMethod,
                                                                 @RequestParam(required = false) Long permissionId) {
        return apiBindingService.listApiPermissionBindings(keyword, httpMethod, permissionId);
    }

    /**
     * Creates an api permission binding.
     */
    @OperationLog(module = "authorization", operation = "CREATE", entityType = "ApiPermissionBinding",
            entityIdAfter = "#result.id",
            entityNameAfter = "#result.pathPattern",
            description = "'新建接口权限绑定'",
            newValueSpel = "#result")
    @PostMapping
    public AdminApiPermissionBindingEntity createApiBinding(@RequestBody AdminApiPermissionBindingEntity request) {
        return apiBindingService.createApiPermissionBinding(request);
    }

    /**
     * Updates an api permission binding.
     */
    @OperationLog(module = "authorization", operation = "UPDATE", entityType = "ApiPermissionBinding",
            entityId = "#p0",
            entityNameAfter = "#result.pathPattern",
            description = "'更新接口权限绑定'",
            oldValueSpel = "@authorizationApiBindingService.apiBindingAuditSnapshot(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{id}")
    public AdminApiPermissionBindingEntity updateApiBinding(@PathVariable Long id,
                                                            @RequestBody AdminApiPermissionBindingEntity request) {
        return apiBindingService.updateApiPermissionBinding(id, request);
    }

    /**
     * Updates api permission binding enabled status.
     */
    @OperationLog(module = "authorization", operation = "UPDATE", entityType = "ApiPermissionBinding",
            entityId = "#p0",
            entityNameAfter = "#result.pathPattern",
            description = "'更新接口权限绑定状态'",
            oldValueSpel = "@authorizationApiBindingService.apiBindingAuditSnapshot(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{id}/status")
    public AdminApiPermissionBindingEntity updateStatus(@PathVariable Long id,
                                                        @RequestBody @Valid EnabledStatusRequest request) {
        return apiBindingService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
    }

    /**
     * Deletes an api permission binding.
     */
    @OperationLog(module = "authorization", operation = "DELETE", entityType = "ApiPermissionBinding",
            entityId = "#p0",
            entityNameAfter = "#result.pathPattern",
            description = "'删除接口权限绑定'",
            oldValueSpel = "@authorizationApiBindingService.apiBindingAuditSnapshot(#p0)",
            recordNewValue = false)
    @DeleteMapping("/{id}")
    public AdminApiPermissionBindingEntity deleteApiBinding(@PathVariable Long id) {
        return apiBindingService.deleteApiPermissionBinding(id);
    }
}
