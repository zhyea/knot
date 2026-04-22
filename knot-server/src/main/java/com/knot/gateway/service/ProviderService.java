package com.knot.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.entity.ProviderEntity;
import com.knot.gateway.mapper.ProviderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProviderService {
    private final ProviderMapper providerMapper;
    private final ObjectMapper objectMapper;

    public ProviderService(ProviderMapper providerMapper, ObjectMapper objectMapper) {
        this.providerMapper = providerMapper;
        this.objectMapper = objectMapper;
    }

    public List<ProviderDto> list() {
        return providerMapper.list().stream().map(this::toDto).toList();
    }

    public ProviderDto create(ProviderDto request) {
        ProviderEntity entity = toEntity(request);
        if (entity.getCode() == null || entity.getCode().isBlank()) {
            entity.setCode("provider_" + System.currentTimeMillis());
        }
        providerMapper.insert(entity);
        return toDto(entity);
    }

    public ProviderDto update(Long id, ProviderDto request) {
        ProviderEntity existing = providerMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provider not found");
        }
        ProviderEntity entity = toEntity(request);
        entity.setId(id);
        entity.setCode(existing.getCode());
        providerMapper.update(entity);
        return toDto(entity);
    }

    private ProviderDto toDto(ProviderEntity e) {
        return new ProviderDto(
                e.getId(),
                e.getCode(),
                e.getName(),
                e.getProviderType(),
                e.getBaseUrl(),
                "ENABLED".equals(e.getStatus()),
                readRate(e.getRateLimitJson()),
                readQuota(e.getQuotaJson())
        );
    }

    private ProviderEntity toEntity(ProviderDto d) {
        ProviderEntity e = new ProviderEntity();
        e.setId(d.id());
        e.setCode(d.code());
        e.setName(d.name());
        e.setProviderType(d.type());
        e.setBaseUrl(d.baseUrl());
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

    public record ProviderDto(Long id, String code, String name, String type, String baseUrl, boolean enabled,
                              RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {}
}
