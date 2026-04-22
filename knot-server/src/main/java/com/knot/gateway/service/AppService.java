package com.knot.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.entity.AppEntity;
import com.knot.gateway.mapper.AppMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppService {
    private final AppMapper appMapper;
    private final ObjectMapper objectMapper;

    public AppService(AppMapper appMapper, ObjectMapper objectMapper) {
        this.appMapper = appMapper;
        this.objectMapper = objectMapper;
    }

    public List<AppDto> list() {
        return appMapper.list().stream().map(this::toDto).toList();
    }

    public AppDto create(AppDto request) {
        AppEntity entity = toEntity(request);
        appMapper.insert(entity);
        return toDto(entity);
    }

    public AppDto update(Long id, AppDto request) {
        AppEntity existing = appMapper.getById(id);
        if (existing == null) throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        AppEntity entity = toEntity(request);
        entity.setId(id);
        entity.setAppId(existing.getAppId());
        appMapper.update(entity);
        return toDto(entity);
    }

    private AppDto toDto(AppEntity e) {
        return new AppDto(e.getId(), e.getAppId(), e.getName(), e.getOwnerUserId(),
                "ENABLED".equals(e.getStatus()), readRate(e.getRateLimitJson()), readQuota(e.getQuotaJson()));
    }

    private AppEntity toEntity(AppDto d) {
        AppEntity e = new AppEntity();
        e.setId(d.id());
        e.setAppId(d.appId());
        e.setName(d.name());
        e.setOwnerUserId(d.ownerUserId());
        e.setStatus(d.enabled() ? "ENABLED" : "DISABLED");
        e.setRateLimitJson(writeJson(d.rateLimitPolicy()));
        e.setQuotaJson(writeJson(d.quotaPolicy()));
        return e;
    }

    private RateLimitPolicy readRate(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, RateLimitPolicy.class); }
        catch (Exception ex) { return null; }
    }

    private QuotaPolicy readQuota(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, QuotaPolicy.class); }
        catch (Exception ex) { return null; }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid policy json"); }
    }

    public record AppDto(Long id, String appId, String name, Long ownerUserId, boolean enabled,
                         RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {}
}
