package org.chobit.knot.admin.controller;

import jakarta.validation.Valid;
import org.chobit.knot.common.annotation.AuthCheck;
import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.svc.vo.auth.AdminModuleDetailItem;
import org.chobit.knot.svc.service.AuthorizationModuleService;
import org.chobit.knot.svc.vo.common.EnabledStatusRequest;
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
 * Module endpoints for authorization management.
 */
@RestController
@RequestMapping("/api/system/authorizations/modules")
@AuthCheck
public class AuthorizationModuleController {

    private final AuthorizationModuleService moduleService;

    /**
     * Constructs a new instance.
     */
    public AuthorizationModuleController(AuthorizationModuleService moduleService) {
        this.moduleService = moduleService;
    }

    /**
     * Lists modules.
     */
    @GetMapping
    public List<AdminModuleDetailItem> listModules(@RequestParam(required = false) String keyword) {
        return moduleService.listModules(keyword);
    }

    /**
     * Creates a module.
     */
    @OperationLog(module = "authorization", operation = "CREATE", entityType = "Module",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.moduleName()",
            description = "'新建模块'",
            newValueSpel = "#result")
    @PostMapping
    public AdminModuleDetailItem createModule(@RequestBody AdminModuleDetailItem request) {
        return moduleService.createModule(request);
    }

    /**
     * Updates a module.
     */
    @OperationLog(module = "authorization", operation = "UPDATE", entityType = "Module",
            entityId = "#p0",
            entityNameAfter = "#result.moduleName()",
            description = "'更新模块'",
            oldValueSpel = "@authorizationModuleService.moduleAuditSnapshot(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{id}")
    public AdminModuleDetailItem updateModule(@PathVariable Long id, @RequestBody AdminModuleDetailItem request) {
        return moduleService.updateModule(id, request);
    }

    /**
     * Updates module enabled status.
     */
    @OperationLog(module = "authorization", operation = "UPDATE", entityType = "Module",
            entityId = "#p0",
            entityNameAfter = "#result.moduleName()",
            description = "'更新模块状态'",
            oldValueSpel = "@authorizationModuleService.moduleAuditSnapshot(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{id}/status")
    public AdminModuleDetailItem updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        return moduleService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
    }

    /**
     * Deletes a module.
     */
    @OperationLog(module = "authorization", operation = "DELETE", entityType = "Module",
            entityId = "#p0",
            entityNameAfter = "#result.moduleName()",
            description = "'删除模块'",
            oldValueSpel = "@authorizationModuleService.moduleAuditSnapshot(#p0)",
            recordNewValue = false)
    @DeleteMapping("/{id}")
    public AdminModuleDetailItem deleteModule(@PathVariable Long id) {
        return moduleService.deleteModule(id);
    }
}
