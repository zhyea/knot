package com.knot.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.entity.ModelEntity;
import com.knot.gateway.mapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelService {
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    public ModelService(ModelMapper modelMapper, ObjectMapper objectMapper) {
        this.modelMapper = modelMapper;
        this.objectMapper = objectMapper;
    }

    public List<ModelDto> list() {
        return modelMapper.list().stream().map(this::toDto).toList();
    }

    public ModelDto create(ModelDto request) {
        ModelEntity entity = toEntity(request);
        if (entity.getModelCode() == null || entity.getModelCode().isBlank()) {
            entity.setModelCode("model_" + System.currentTimeMillis());
        }
        modelMapper.insert(entity);
        return toDto(entity);
    }

    public ModelDto update(Long id, ModelDto request) {
        ModelEntity existing = modelMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
        }
        ModelEntity entity = toEntity(request);
        entity.setId(id);
        entity.setModelCode(existing.getModelCode());
        modelMapper.update(entity);
        return toDto(entity);
    }

    private ModelDto toDto(ModelEntity e) {
        return new ModelDto(
                e.getId(), e.getModelCode(), e.getName(), e.getProviderId(), e.getModelType(), e.getVersion(),
                "ENABLED".equals(e.getStatus()), readRate(e.getRateLimitJson()), readQuota(e.getQuotaJson())
        );
    }

    private ModelEntity toEntity(ModelDto d) {
        ModelEntity e = new ModelEntity();
        e.setId(d.id());
        e.setModelCode(d.modelCode());
        e.setName(d.name());
        e.setProviderId(d.providerId());
        e.setModelType(d.modelType());
        e.setVersion(d.version());
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

    public record ModelDto(Long id, String modelCode, String name, Long providerId, String modelType, String version,
                           boolean enabled, RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {}
}
