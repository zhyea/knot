package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.util.List;
import java.util.Map;
import org.chobit.knot.common.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.svc.converter.AppConverter;
import org.chobit.knot.svc.dto.app.AppDto;
import org.chobit.knot.dal.entity.AppEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.AppMapper;
import org.chobit.knot.dal.mapper.DepartmentMapper;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.svc.service.support.AuditSnapshotSupport;
import org.chobit.knot.svc.service.support.CodeBindingSupport;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AppService {

    private final AppMapper appMapper;
    private final AppConverter appConverter;
    private final DepartmentMapper departmentMapper;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    /**
     * Constructs a new instance.
     */
    public AppService(AppMapper appMapper,
                      AppConverter appConverter,
                      DepartmentMapper departmentMapper,
                      ResourceTrafficPolicySupport trafficPolicySupport) {
        this.appMapper = appMapper;
        this.appConverter = appConverter;
        this.departmentMapper = departmentMapper;
        this.trafficPolicySupport = trafficPolicySupport;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<AppDto> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<AppDto> list(PageRequest pageRequest, String keyword) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<AppEntity> pageInfo = new PageInfo<>(appMapper.list(normalizeKeyword(keyword)));
            List<AppDto> dtos = pageInfo.getList().stream()
                    .map(e -> enrich(appConverter.toDto(e), e.getId()))
                    .toList();
            return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public AppDto getById(Long id) {
        AppEntity entity = appMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        }
        return enrich(appConverter.toDto(entity), entity.getId());
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public AppDto create(AppDto request) {
        String appCode = request.appCode() != null ? request.appCode().trim() : "";
        if (appCode.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "please input App Code");
        }
        Long existingApp = appMapper.countByAppCode(appCode);
        if (existingApp != null && existingApp > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "App Code already exists: " + appCode);
        }
        validateDepartment(request.deptCode());
        AppEntity entity = appConverter.toEntity(request);
        entity.setAppCode(appCode);
        appMapper.insert(entity);
        trafficPolicySupport.save(TrafficResourceTypeEnum.APP.code(), entity.getId(),
                request.rateLimitPolicy(), request.quotaPolicies());
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public AppDto update(Long id, AppDto request) {
        AppEntity existing = appMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        }
        validateDepartment(request.deptCode());
        AppEntity entity = appConverter.toEntity(request);
        entity.setId(id);
        entity.setAppCode(resolveAppCodeForUpdate(existing, request.appCode()));
        appMapper.update(entity);
        trafficPolicySupport.save(TrafficResourceTypeEnum.APP.code(), id,
                request.rateLimitPolicy(), request.quotaPolicies());
        return getById(id);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void delete(Long id) {
        AppEntity existing = appMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在");
        }
        int rows = appMapper.softDelete(id);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在或已删除");
        }
    }

    /**
     * 应用编码改码：未被路由规则引用时可改，已绑定直接 409；改码前做唯一性校验。
     *
     * <p>入参 appCode 为空表示「不修改」，沿用库中现值。</p>
     */
    private String resolveAppCodeForUpdate(AppEntity existing, String requested) {
        String appCode = CodeBindingSupport.normalize(requested);
        if (appCode.isEmpty()) {
            return existing.getAppCode();
        }
        CodeBindingSupport.requireCodeChangeAllowed(
                existing.getAppCode(), appCode, existing.getBoundCount(), "应用编码");
        if (!CodeBindingSupport.isSameCode(existing.getAppCode(), appCode)) {
            Long duplicated = appMapper.countByAppCode(appCode);
            if (duplicated != null && duplicated > 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "应用编码「" + appCode + "」已存在");
            }
        }
        return appCode;
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
    }

    /**
     * 操作日志用的应用快照：{@code @OperationLog} 的 SpEL 在方法前后各取一次，
     * 实体不存在时返回 {@code null}（重复删除不改写业务结果）。
     */
    public Map<String, Object> appAuditSnapshot(Long id) {
        return AuditSnapshotSupport.capture(id, key -> JsonKit.toMap(getById(key)));
    }

    private AppDto enrich(AppDto base, Long appId) {
        TrafficPolicies traffic =
                trafficPolicySupport.load(TrafficResourceTypeEnum.APP.code(), appId);
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        List<QuotaPolicy> quotas = traffic != null ? traffic.quotaPolicies() : List.of();
        return new AppDto(
                base.id(), base.appCode(), base.name(), base.deptCode(), base.deptName(),
                base.ownerUsername(), base.ownerName(), base.remark(),
                rate, quotas, base.boundCount()
        );
    }

    /**
     * 部门绑定走业务码 dept_code（非主键 id），按 code 校验存在性。
     */
    private void validateDepartment(String deptCode) {
        if (deptCode == null || deptCode.isBlank()) {
            return;
        }
        if (departmentMapper.getByCode(deptCode) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "department not found");
        }
    }
}
