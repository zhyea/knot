package org.chobit.knot.svc.service;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.dal.entity.AdminApiPermissionBindingEntity;
import org.chobit.knot.svc.converter.AuthorizationConverter;
import org.chobit.knot.svc.service.support.AuthorizationManageSupport;
import org.chobit.knot.svc.vo.auth.AdminApiBindingItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Api permission binding management service for authorization administration.
 *
 * <p>⭐ 对外只吐 {@link AdminApiBindingItem}，实体不出本层（规范 §3.1：「边界在跨模块出去的那一刻」）。
 * 入参同样是 Item——{@code @OperationLog} 的 {@code oldValueSpel} 会调
 * {@link #apiBindingAuditSnapshot}，新旧快照必须是同一种载体，否则审计里两个 JSON 的字段集会不一致。</p>
 */
@Service
public class AuthorizationApiBindingService {

    private final AuthorizationManageSupport support;
    private final AuthorizationConverter converter;

    /**
     * Constructs a new instance.
     */
    public AuthorizationApiBindingService(AuthorizationManageSupport support,
                                          AuthorizationConverter converter) {
        this.support = support;
        this.converter = converter;
    }

    /**
     * Lists api permission bindings.
     */
    public List<AdminApiBindingItem> listApiPermissionBindings(String keyword, String httpMethod, Long permissionId) {
        return converter.toApiBindingItemList(
                support.mapper().listApiPermissionBindings(StringUtils.trimToNull(keyword),
                        StringUtils.trimToNull(httpMethod), permissionId));
    }

    /**
     * Creates an api permission binding.
     */
    @Transactional
    public AdminApiBindingItem createApiPermissionBinding(AdminApiBindingItem request) {
        AdminApiPermissionBindingEntity entity = converter.toApiBindingEntity(request);
        support.validateApiPermissionBinding(entity, null);
        support.mapper().insertApiPermissionBinding(entity);
        // 主键由 insert 回填到 entity —— record 不可变，回头读 request.id() 仍是 null
        return converter.toApiBindingItem(support.getApiPermissionBindingById(entity.getId()));
    }

    /**
     * Updates an api permission binding.
     */
    @Transactional
    public AdminApiBindingItem updateApiPermissionBinding(Long id, AdminApiBindingItem request) {
        AdminApiPermissionBindingEntity existing = support.getApiPermissionBindingById(id);
        AdminApiPermissionBindingEntity entity = converter.toApiBindingEntity(request);
        entity.setId(id);
        if (entity.getStatus() == null) {
            entity.setStatus(existing.getStatus());
        }
        support.validateApiPermissionBinding(entity, id);
        support.mapper().updateApiPermissionBinding(entity);
        return converter.toApiBindingItem(support.getApiPermissionBindingById(id));
    }

    /**
     * Updates api permission binding enabled status only.
     */
    @Transactional
    public AdminApiBindingItem updateStatus(Long id, boolean enabled) {
        support.getApiPermissionBindingById(id);
        support.mapper().updateApiPermissionBindingStatus(id, EnabledStatusEnum.codeOf(enabled));
        return converter.toApiBindingItem(support.getApiPermissionBindingById(id));
    }

    /**
     * Deletes an api permission binding.
     */
    @Transactional
    public AdminApiBindingItem deleteApiPermissionBinding(Long id) {
        AdminApiPermissionBindingEntity existing = support.getApiPermissionBindingById(id);
        support.mapper().deleteApiPermissionBinding(id);
        return converter.toApiBindingItem(existing);
    }

    /**
     * Returns an api permission binding snapshot for operation log auditing.
     *
     * <p>仅由 {@code @OperationLog} 的 SpEL 表达式（{@code @authorizationApiBindingService.apiBindingAuditSnapshot(#p0)}）
     * 反射调用，Java 侧无直接引用，IDE 会误报 unused。</p>
     */
    @SuppressWarnings("unused")
    public AdminApiBindingItem apiBindingAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        return converter.toApiBindingItem(support.getApiPermissionBindingById(id));
    }
}
