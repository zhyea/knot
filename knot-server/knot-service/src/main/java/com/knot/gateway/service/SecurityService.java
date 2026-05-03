package com.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.SecurityConverter;
import com.knot.gateway.dto.security.AlertItemDto;
import com.knot.gateway.dto.security.CacheEvictResultDto;
import com.knot.gateway.dto.security.SecurityOverviewDto;
import com.knot.gateway.dto.security.SecurityPolicyDto;
import com.knot.gateway.entity.AlertEntity;
import com.knot.gateway.entity.SecurityPolicyEntity;
import com.knot.gateway.mapper.SecurityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SecurityService {
    private static final double DEFAULT_CACHE_HIT_RATE = 95.0;

    private final SecurityMapper securityMapper;
    private final SecurityConverter securityConverter;

    public SecurityService(SecurityMapper securityMapper, SecurityConverter securityConverter) {
        this.securityMapper = securityMapper;
        this.securityConverter = securityConverter;
    }

    public SecurityOverviewDto overview() {
        Integer alertCount = securityMapper.countOpenAlerts();
        if (alertCount == null) {
            alertCount = 0;
        }
        Double ratio = securityMapper.cacheActiveRatioPercent();
        double cacheHitRate = ratio == null ? DEFAULT_CACHE_HIT_RATE : ratio;
        return new SecurityOverviewDto(true, true, 0, alertCount, cacheHitRate);
    }

    @Transactional
    public SecurityPolicyDto updatePolicy(SecurityPolicyDto request) {
        SecurityPolicyEntity entity = new SecurityPolicyEntity();
        entity.setPolicyCode(request.policyCode());
        entity.setConfigJson(request.configJson());
        entity.setStatus(request.status());
        int updated = securityMapper.updatePolicy(entity);
        if (updated == 0) {
            securityMapper.insertPolicy(entity);
        }
        return request;
    }

    public PageResult<AlertItemDto> listAlerts(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<AlertEntity> pageInfo = new PageInfo<>(securityMapper.listAlerts());
        return PageResult.fromPage(pageInfo, securityConverter::toAlertItemDtoList, pageRequest);
    }

    @Transactional
    public CacheEvictResultDto evictCache(String cacheKey, String cacheType) {
        String type = cacheType == null || cacheType.isBlank() ? "GENERIC" : cacheType;
        securityMapper.upsertCacheEvict(cacheKey, type);
        return new CacheEvictResultDto(cacheKey, "EVICTED");
    }

}
