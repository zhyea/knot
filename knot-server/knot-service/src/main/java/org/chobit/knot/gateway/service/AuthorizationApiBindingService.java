package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.entity.AdminApiPermissionBindingEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Api permission binding management service for authorization administration.
 */
@Service
public class AuthorizationApiBindingService {

    private final AuthorizationManageSupport support;

    /**
     * Constructs a new instance.
     */
    public AuthorizationApiBindingService(AuthorizationManageSupport support) {
        this.support = support;
    }

    /**
     * Lists api permission bindings.
     */
    public List<AdminApiPermissionBindingEntity> listApiPermissionBindings(String keyword, String httpMethod, Long permissionId) {
        return support.mapper().listApiPermissionBindings(support.normalizeKeyword(keyword), support.normalizeKeyword(httpMethod), permissionId);
    }

    /**
     * Creates an api permission binding.
     */
    @Transactional
    public AdminApiPermissionBindingEntity createApiPermissionBinding(AdminApiPermissionBindingEntity request) {
        support.validateApiPermissionBinding(request, null);
        support.mapper().insertApiPermissionBinding(request);
        return support.getApiPermissionBindingById(request.getId());
    }

    /**
     * Updates an api permission binding.
     */
    @Transactional
    public AdminApiPermissionBindingEntity updateApiPermissionBinding(Long id, AdminApiPermissionBindingEntity request) {
        AdminApiPermissionBindingEntity existing = support.getApiPermissionBindingById(id);
        request.setId(id);
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            request.setStatus(existing.getStatus());
        }
        support.validateApiPermissionBinding(request, id);
        support.mapper().updateApiPermissionBinding(request);
        return support.getApiPermissionBindingById(id);
    }

    /**
     * Updates api permission binding enabled status only.
     */
    @Transactional
    public AdminApiPermissionBindingEntity updateStatus(Long id, boolean enabled) {
        support.getApiPermissionBindingById(id);
        support.mapper().updateApiPermissionBindingStatus(id, enabled ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
        return support.getApiPermissionBindingById(id);
    }

    /**
     * Deletes an api permission binding.
     */
    @Transactional
    public AdminApiPermissionBindingEntity deleteApiPermissionBinding(Long id) {
        AdminApiPermissionBindingEntity existing = support.getApiPermissionBindingById(id);
        support.mapper().deleteApiPermissionBinding(id);
        return existing;
    }
}
