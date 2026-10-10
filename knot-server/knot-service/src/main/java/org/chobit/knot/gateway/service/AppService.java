package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.converter.AppConverter;
import org.chobit.knot.gateway.dto.app.AppDto;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.mapper.DepartmentMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        if (countPositive(appMapper.countByAppCode(appCode))) {
            throw new BusinessException(ErrorCode.CONFLICT, "App Code already exists: " + appCode);
        }
        validateDepartment(request.deptCode());
        AppEntity entity = appConverter.toEntity(request);
        entity.setAppCode(appCode);
        appMapper.insert(entity);
        trafficPolicySupport.save(TrafficResourceTypeEnum.APP.code(), entity.getId(),
                request.rateLimitPolicy(), request.quotaPolicy());
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
        entity.setAppCode(existing.getAppCode());
        appMapper.update(entity);
        trafficPolicySupport.save(TrafficResourceTypeEnum.APP.code(), id,
                request.rateLimitPolicy(), request.quotaPolicy());
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
        assertNotInUse(existing);
        int rows = appMapper.softDelete(id);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在或已删除");
        }
    }

    /**
     * 删除前置校验：凭据按应用业务码（app_code）绑定，模型权限按应用主键 id（app_id）绑定，两者参数语义不同。
     */
    private void assertNotInUse(AppEntity app) {
        List<String> reasons = new ArrayList<>();
        if (countPositive(appMapper.countCredentialsByAppCode(app.getAppCode()))) {
            reasons.add("已配置 API 凭证");
        }
        if (countPositive(appMapper.countModelPermissionsByAppId(app.getId()))) {
            reasons.add("已分配模型权限");
        }
        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该应用正在被使用，无法删除：" + String.join("，", reasons));
        }
    }

    private static boolean countPositive(Long count) {
        return count != null && count > 0;
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public Map<String, Object> appAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            AppDto dto = getById(id);
            return JsonKit.toMap(dto);
        } catch (BusinessException e) {
            return null;
        }
    }

    private AppDto enrich(AppDto base, Long appId) {
        TrafficPolicies traffic =
                trafficPolicySupport.load(TrafficResourceTypeEnum.APP.code(), appId);
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        QuotaPolicy quota = traffic != null ? traffic.quotaPolicy() : null;
        return new AppDto(
                base.id(), base.appCode(), base.name(), base.deptCode(), base.deptName(),
                base.ownerUsername(), base.ownerName(), base.remark(),
                rate, quota
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
