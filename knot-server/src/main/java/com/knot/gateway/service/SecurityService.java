package com.knot.gateway.service;

import com.knot.gateway.entity.AlertEntity;
import com.knot.gateway.entity.SecurityPolicyEntity;
import com.knot.gateway.mapper.SecurityMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SecurityService {
    private static final double DEFAULT_CACHE_HIT_RATE = 95.0;

    private final SecurityMapper securityMapper;

    public SecurityService(SecurityMapper securityMapper) {
        this.securityMapper = securityMapper;
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

    public List<AlertItemDto> listAlerts() {
        return securityMapper.listAlerts().stream()
                .map(a -> new AlertItemDto("ALERT-" + a.getId(), a.getLevel(), a.getTitle(), a.getStatus()))
                .toList();
    }

    public CacheEvictResultDto evictCache(String cacheKey, String cacheType) {
        String type = cacheType == null || cacheType.isBlank() ? "GENERIC" : cacheType;
        securityMapper.upsertCacheEvict(cacheKey, type);
        return new CacheEvictResultDto(cacheKey, "EVICTED");
    }

    public record SecurityOverviewDto(boolean authEnabled, boolean signVerificationEnabled, int blockedIpCount,
                                      int alertCount, double cacheHitRate) {
    }

    public record SecurityPolicyDto(String policyCode, String configJson, String status) {
    }

    public record AlertItemDto(String alertId, String level, String title, String status) {
    }

    public record CacheEvictResultDto(String cacheKey, String result) {
    }
}
