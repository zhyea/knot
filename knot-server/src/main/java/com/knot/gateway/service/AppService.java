package com.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.converter.AppConverter;
import com.knot.gateway.entity.AppEntity;
import com.knot.gateway.mapper.AppMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AppService {
    private final AppMapper appMapper;
    private final AppConverter appConverter;

    public AppService(AppMapper appMapper, AppConverter appConverter) {
        this.appMapper = appMapper;
        this.appConverter = appConverter;
    }

    public PageResult<AppDto> list(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<AppEntity> pageInfo = new PageInfo<>(appMapper.list());
        return PageResult.fromPage(pageInfo, appConverter::toDtoList, pageRequest);
    }

    public AppDto getById(Long id) {
        AppEntity entity = appMapper.getById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        return appConverter.toDto(entity);
    }

    @Transactional
    public AppDto create(AppDto request) {
        AppEntity entity = appConverter.toEntity(request);
        appMapper.insert(entity);
        return appConverter.toDto(entity);
    }

    @Transactional
    public AppDto update(Long id, AppDto request) {
        AppEntity existing = appMapper.getById(id);
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        AppEntity entity = appConverter.toEntity(request);
        entity.setId(id);
        entity.setAppId(existing.getAppId());
        appMapper.update(entity);
        return appConverter.toDto(entity);
    }

    public AppMetricsDto getAppMetrics(Long id) {
        // 从 gateway_requests 聚合统计
        Long total = appMapper.countRequestsByAppId(id);
        Long success = appMapper.countSuccessByAppId(id);
        Long tokens = appMapper.sumTokensByAppId(id);
        long totalVal = total != null ? total : 0L;
        long successVal = success != null ? success : 0L;
        long tokenVal = tokens != null ? tokens : 0L;
        return new AppMetricsDto(id, totalVal, successVal, totalVal - successVal, tokenVal);
    }

    public record AppDto(Long id, String appId, String name, Long ownerUserId, boolean enabled,
                         RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {}

    public record AppMetricsDto(Long appInternalId, Long totalRequests, Long successRequests, Long failedRequests, Long tokenUsage) {}
}
