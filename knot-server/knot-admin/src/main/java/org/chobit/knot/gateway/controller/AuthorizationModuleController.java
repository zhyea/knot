package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.entity.AdminModuleEntity;
import org.chobit.knot.gateway.service.AuthorizationModuleService;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
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
    public List<AdminModuleEntity> listModules(@RequestParam(required = false) String keyword) {
        return moduleService.listModules(keyword);
    }

    /**
     * Creates a module.
     */
    @PostMapping
    public AdminModuleEntity createModule(@RequestBody AdminModuleEntity request) {
        return moduleService.createModule(request);
    }

    /**
     * Updates a module.
     */
    @PutMapping("/{id}")
    public AdminModuleEntity updateModule(@PathVariable Long id, @RequestBody AdminModuleEntity request) {
        return moduleService.updateModule(id, request);
    }

    /**
     * Updates module enabled status.
     */
    @PutMapping("/{id}/status")
    public AdminModuleEntity updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        return moduleService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
    }

    /**
     * Deletes a module.
     */
    @DeleteMapping("/{id}")
    public AdminModuleEntity deleteModule(@PathVariable Long id) {
        return moduleService.deleteModule(id);
    }
}
