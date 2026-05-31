package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.converter.AppConverter;
import org.chobit.knot.gateway.dto.app.AppDto;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AppService {

    private final AppMapper appMapper;
    private final AppConverter appConverter;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    /**
     * Constructs a new instance.
     */
    public AppService(AppMapper appMapper,
                      AppConverter appConverter,
                      ResourceTrafficPolicySupport trafficPolicySupport) {
        this.appMapper = appMapper;
        this.appConverter = appConverter;
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
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<AppEntity> pageInfo = new PageInfo<>(appMapper.list(normalizeKeyword(keyword)));
        List<AppDto> dtos = pageInfo.getList().stream()
                .map(e -> enrich(appConverter.toDto(e), e.getId()))
                .toList();
        return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
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
        String appId = request.appId() != null ? request.appId().trim() : "";
        if (appId.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "please input App ID");
        }
        if (countPositive(appMapper.countByAppId(appId))) {
            throw new BusinessException(ErrorCode.CONFLICT, "App ID already exists: " + appId);
        }
        AppEntity entity = appConverter.toEntity(request);
        entity.setAppId(appId);
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
        AppEntity entity = appConverter.toEntity(request);
        entity.setId(id);
        entity.setAppId(existing.getAppId());
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        }
        assertNotInUse(id);
        int rows = appMapper.softDelete(id);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        }
    }

    private void assertNotInUse(Long appId) {
        List<String> reasons = new ArrayList<>();
        if (countPositive(appMapper.countCredentialsByAppId(appId))) {
            reasons.add("API credentials configured");
        }
        if (countPositive(appMapper.countModelPermissionsByAppId(appId))) {
            reasons.add("model permissions assigned");
        }
        if (!reasons.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "app is in use and cannot be deleted: " + String.join(", ", reasons));
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
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", dto.id());
            m.put("appId", dto.appId());
            m.put("name", dto.name());
            m.put("ownerUserId", dto.ownerUserId());
            m.put("ownerName", dto.ownerName());
            m.put("remark", dto.remark());
            m.put("rateLimitPolicy", dto.rateLimitPolicy());
            m.put("quotaPolicy", dto.quotaPolicy());
            return m;
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
                base.id(), base.appId(), base.name(), base.ownerUserId(), base.ownerName(), base.remark(),
                rate, quota
        );
    }
}
