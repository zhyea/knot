package org.chobit.knot.gateway.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MapStruct 共享转换方法：status↔enabled、JSON↔Object
 */
@Component
public class CommonMappings {

    private final ObjectMapper objectMapper;

    public CommonMappings(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ==================== status ↔ enabled ====================

    @Named("statusToEnabled")
    public boolean statusToEnabled(String status) {
        return "ENABLED".equals(status);
    }

    @Named("enabledToStatus")
    public String enabledToStatus(boolean enabled) {
        return enabled ? "ENABLED" : "DISABLED";
    }

    // ==================== JSON ↔ RateLimitPolicy ====================

    @Named("jsonToRateLimit")
    public RateLimitPolicy jsonToRateLimit(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, RateLimitPolicy.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    @Named("rateLimitToJson")
    public String rateLimitToJson(RateLimitPolicy policy) {
        if (policy == null) return null;
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid rate limit policy json");
        }
    }

    // ==================== JSON ↔ QuotaPolicy ====================

    @Named("jsonToQuota")
    public QuotaPolicy jsonToQuota(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, QuotaPolicy.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    @Named("quotaToJson")
    public String quotaToJson(QuotaPolicy policy) {
        if (policy == null) return null;
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid quota policy json");
        }
    }

    // ==================== Long id → Alert ID (ALERT- 前缀) ====================

    @Named("idToAlertId")
    public String idToAlertId(Long id) {
        return id == null ? "ALERT-0" : "ALERT-" + id;
    }

    // ==================== String ↔ Long (owner 字段) ====================

    @Named("stringToLong")
    public Long stringToLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    // ==================== JSON ↔ List<Integer> (steps) ====================

    @Named("jsonToSteps")
    public List<Integer> jsonToSteps(String json) {
        if (json == null || json.isBlank()) return List.of(10, 30, 50, 100);
        try {
            List<Integer> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            return (parsed == null || parsed.isEmpty()) ? List.of(10, 30, 50, 100) : List.copyOf(parsed);
        } catch (JsonProcessingException ex) {
            return List.of(10, 30, 50, 100);
        }
    }

    @Named("stepsToJson")
    public String stepsToJson(List<Integer> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "steps json serialize failed");
        }
    }
}
