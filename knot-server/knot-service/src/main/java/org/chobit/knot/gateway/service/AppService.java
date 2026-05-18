package org.chobit.knot.gateway.service;



import com.github.pagehelper.PageHelper;

import com.github.pagehelper.PageInfo;

import org.chobit.knot.gateway.error.BusinessException;

import org.chobit.knot.gateway.error.ErrorCode;

import org.chobit.knot.gateway.model.PageRequest;

import org.chobit.knot.gateway.model.PageResult;

import org.chobit.knot.gateway.converter.AppConverter;

import org.chobit.knot.gateway.dto.app.AppDto;

import org.chobit.knot.gateway.dto.app.AppMetricsDto;

import org.chobit.knot.gateway.constants.TrafficResourceType;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

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

    public AppService(AppMapper appMapper,
                      AppConverter appConverter,
                      ResourceTrafficPolicySupport trafficPolicySupport) {
        this.appMapper = appMapper;
        this.appConverter = appConverter;
        this.trafficPolicySupport = trafficPolicySupport;
    }



    public PageResult<AppDto> list(PageRequest pageRequest) {

        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());

        PageInfo<AppEntity> pageInfo = new PageInfo<>(appMapper.list());

        List<AppDto> dtos = pageInfo.getList().stream()
                .map(e -> enrich(appConverter.toDto(e), e.getId()))
                .toList();
        return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }



    public AppDto getById(Long id) {

        AppEntity entity = appMapper.getById(id);

        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在");

        return enrich(appConverter.toDto(entity), entity.getId());
    }



    @Transactional
    public AppDto create(AppDto request) {
        String appId = request.appId() != null ? request.appId().trim() : "";
        if (appId.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写 App ID");
        }
        if (countPositive(appMapper.countByAppId(appId))) {
            throw new BusinessException(ErrorCode.CONFLICT, "App ID「" + appId + "」已存在，请更换后重试");
        }
        AppEntity entity = appConverter.toEntity(request);
        entity.setAppId(appId);
        appMapper.insert(entity);
        trafficPolicySupport.save(TrafficResourceType.APP, entity.getId(),
                request.rateLimitPolicy(), request.quotaPolicy());
        return getById(entity.getId());
    }



    @Transactional

    public AppDto update(Long id, AppDto request) {

        AppEntity existing = appMapper.getById(id);

        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在");

        AppEntity entity = appConverter.toEntity(request);

        entity.setId(id);

        entity.setAppId(existing.getAppId());

        appMapper.update(entity);
        trafficPolicySupport.save(TrafficResourceType.APP, id,
                request.rateLimitPolicy(), request.quotaPolicy());
        return getById(id);
    }



    @Transactional

    public void delete(Long id) {

        AppEntity existing = appMapper.getById(id);

        if (existing == null) {

            throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在");

        }

        assertNotInUse(id);

        int rows = appMapper.softDelete(id);

        if (rows == 0) {

            throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在");

        }

    }



    private void assertNotInUse(Long appId) {

        List<String> reasons = new ArrayList<>();

        if (countPositive(appMapper.countCredentialsByAppId(appId))) {

            reasons.add("已配置 API 凭证");

        }

        if (countPositive(appMapper.countModelPermissionsByAppId(appId))) {

            reasons.add("已分配模型权限");

        }

        if (countPositive(appMapper.countRequestsByAppId(appId))) {

            reasons.add("存在网关调用记录");

        }

        if (countPositive(appMapper.countBillingStatementsByAppId(appId))) {

            reasons.add("存在计费账单记录");

        }

        if (!reasons.isEmpty()) {

            throw new BusinessException(ErrorCode.CONFLICT,

                    "应用已被使用，无法删除：" + String.join("、", reasons));

        }

    }



    private static boolean countPositive(Long count) {

        return count != null && count > 0;

    }



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

    public AppMetricsDto getAppMetrics(Long id) {

        getById(id);

        Long total = appMapper.countRequestsByAppId(id);

        Long success = appMapper.countSuccessByAppId(id);

        Long tokens = appMapper.sumTokensByAppId(id);

        long totalVal = total != null ? total : 0L;

        long successVal = success != null ? success : 0L;

        long tokenVal = tokens != null ? tokens : 0L;

        return new AppMetricsDto(id, totalVal, successVal, totalVal - successVal, tokenVal);
    }

    private AppDto enrich(AppDto base, Long appId) {
        ResourceTrafficPolicySupport.TrafficPolicies traffic =
                trafficPolicySupport.load(TrafficResourceType.APP, appId);
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        QuotaPolicy quota = traffic != null ? traffic.quotaPolicy() : null;
        return new AppDto(
                base.id(), base.appId(), base.name(), base.ownerUserId(), base.ownerName(), base.remark(),
                rate, quota
        );
    }
}

