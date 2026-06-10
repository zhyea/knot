package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.entity.AdminModuleEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Module management service for authorization administration.
 */
@Service
public class AuthorizationModuleService {

    private final AuthorizationManageSupport support;

    /**
     * Constructs a new instance.
     */
    public AuthorizationModuleService(AuthorizationManageSupport support) {
        this.support = support;
    }

    /**
     * Lists modules.
     */
    public List<AdminModuleEntity> listModules(String keyword) {
        return support.mapper().listModules(support.normalizeKeyword(keyword));
    }

    /**
     * Creates a module.
     */
    @Transactional
    public AdminModuleEntity createModule(AdminModuleEntity request) {
        support.validateModule(request, null);
        support.mapper().insertModule(request);
        return support.getModuleById(request.getId());
    }

    /**
     * Updates a module.
     */
    @Transactional
    public AdminModuleEntity updateModule(Long id, AdminModuleEntity request) {
        AdminModuleEntity existing = support.getModuleById(id);
        request.setId(id);
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            request.setStatus(existing.getStatus());
        }
        support.validateModule(request, id);
        support.mapper().updateModule(request);
        return support.getModuleById(id);
    }

    /**
     * Updates module enabled status only.
     */
    @Transactional
    public AdminModuleEntity updateStatus(Long id, boolean enabled) {
        support.getModuleById(id);
        support.mapper().updateModuleStatus(id, enabled ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
        return support.getModuleById(id);
    }

    /**
     * Deletes a module.
     */
    @Transactional
    public AdminModuleEntity deleteModule(Long id) {
        AdminModuleEntity existing = support.getModuleById(id);
        support.ensureModuleUnused(id);
        support.mapper().deleteModule(id);
        return existing;
    }
}
