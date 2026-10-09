package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
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
        if (request.getStatus() == null) {
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
        support.mapper().updateModuleStatus(id, EnabledStatusEnum.codeOf(enabled));
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

    /**
     * Returns a module snapshot for operation log auditing.
     *
     * <p>仅由 {@code @OperationLog} 的 SpEL 表达式（{@code @authorizationModuleService.moduleAuditSnapshot(#p0)}）
     * 反射调用，Java 侧无直接引用，IDE 会误报 unused。
     */
    @SuppressWarnings("unused")
    public AdminModuleEntity moduleAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        return support.getModuleById(id);
    }
}
