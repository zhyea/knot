package org.chobit.knot.svc.service;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.dal.entity.AdminModuleEntity;
import org.chobit.knot.svc.converter.AuthorizationConverter;
import org.chobit.knot.svc.service.support.AuthorizationManageSupport;
import org.chobit.knot.svc.vo.auth.AdminModuleDetailItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Module management service for authorization administration.
 *
 * <p>⭐ 对外载体是 {@link AdminModuleDetailItem}，不是导航树用的 {@code AdminModuleItem}：
 * 后者字段名是 {@code moduleId} 且带 {@code menus} 树，是布局渲染的专用造型。</p>
 */
@Service
public class AuthorizationModuleService {

    private final AuthorizationManageSupport support;
    private final AuthorizationConverter converter;

    /**
     * Constructs a new instance.
     */
    public AuthorizationModuleService(AuthorizationManageSupport support,
                                      AuthorizationConverter converter) {
        this.support = support;
        this.converter = converter;
    }

    /**
     * Lists modules.
     */
    public List<AdminModuleDetailItem> listModules(String keyword) {
        return converter.toModuleItemList(support.mapper().listModules(StringUtils.trimToNull(keyword)));
    }

    /**
     * Creates a module.
     */
    @Transactional
    public AdminModuleDetailItem createModule(AdminModuleDetailItem request) {
        AdminModuleEntity entity = converter.toModuleEntity(request);
        support.validateModule(entity, null);
        support.mapper().insertModule(entity);
        // 主键由 insert 回填到 entity —— record 不可变，回头读 request.id() 仍是 null
        return converter.toModuleItem(support.getModuleById(entity.getId()));
    }

    /**
     * Updates a module.
     */
    @Transactional
    public AdminModuleDetailItem updateModule(Long id, AdminModuleDetailItem request) {
        AdminModuleEntity existing = support.getModuleById(id);
        AdminModuleEntity entity = converter.toModuleEntity(request);
        entity.setId(id);
        if (entity.getStatus() == null) {
            entity.setStatus(existing.getStatus());
        }
        support.validateModule(entity, id);
        support.mapper().updateModule(entity);
        return converter.toModuleItem(support.getModuleById(id));
    }

    /**
     * Updates module enabled status only.
     */
    @Transactional
    public AdminModuleDetailItem updateStatus(Long id, boolean enabled) {
        support.getModuleById(id);
        support.mapper().updateModuleStatus(id, EnabledStatusEnum.codeOf(enabled));
        return converter.toModuleItem(support.getModuleById(id));
    }

    /**
     * Deletes a module.
     */
    @Transactional
    public AdminModuleDetailItem deleteModule(Long id) {
        AdminModuleEntity existing = support.getModuleById(id);
        support.ensureModuleUnused(id);
        support.mapper().deleteModule(id);
        return converter.toModuleItem(existing);
    }

    /**
     * Returns a module snapshot for operation log auditing.
     *
     * <p>仅由 {@code @OperationLog} 的 SpEL 表达式（{@code @authorizationModuleService.moduleAuditSnapshot(#p0)}）
     * 反射调用，Java 侧无直接引用，IDE 会误报 unused。</p>
     */
    @SuppressWarnings("unused")
    public AdminModuleDetailItem moduleAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        return converter.toModuleItem(support.getModuleById(id));
    }
}
