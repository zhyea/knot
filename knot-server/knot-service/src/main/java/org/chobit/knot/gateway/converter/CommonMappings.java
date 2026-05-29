package org.chobit.knot.gateway.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.constants.EntityStatus;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.util.JsonKit;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MapStruct 共享转换方法：status↔enabled、JSON↔Object
 */
@Component
public class CommonMappings {

    // ==================== status ↔ enabled ====================

    @Named("statusToEnabled")
    public boolean statusToEnabled(String status) {
        return EntityStatus.ENABLED.equals(status);
    }

    @Named("enabledToStatus")
    public String enabledToStatus(boolean enabled) {
        return enabled ? EntityStatus.ENABLED : EntityStatus.DISABLED;
    }

    @Named("billingStatusToEnabled")
    public boolean billingStatusToEnabled(String status) {
        if (status == null) {
            return false;
        }
        return EntityStatus.ACTIVE.equalsIgnoreCase(status) || EntityStatus.ENABLED.equalsIgnoreCase(status);
    }

    @Named("billingEnabledToStatus")
    public String billingEnabledToStatus(boolean enabled) {
        return enabled ? EntityStatus.ACTIVE : EntityStatus.INACTIVE;
    }

    // ==================== JSON ↔ RateLimitPolicy ====================

    @Named("jsonToRateLimit")
    public RateLimitPolicy jsonToRateLimit(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonKit.fromJson(json, RateLimitPolicy.class);
    }

    @Named("rateLimitToJson")
    public String rateLimitToJson(RateLimitPolicy policy) {
        if (policy == null) return null;
        String json = JsonKit.toJson(policy);
        if (json == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid rate limit policy json");
        }
        return json;
    }

    // ==================== JSON ↔ QuotaPolicy ====================

    @Named("jsonToQuota")
    public QuotaPolicy jsonToQuota(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonKit.fromJson(json, QuotaPolicy.class);
    }

    @Named("quotaToJson")
    public String quotaToJson(QuotaPolicy policy) {
        if (policy == null) return null;
        String json = JsonKit.toJson(policy);
        if (json == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid quota policy json");
        }
        return json;
    }

    // ==================== JSON ↔ Map (认证配置等) ====================

    @Named("jsonToStringObjectMap")
    public Map<String, Object> jsonToStringObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> parsed = JsonKit.fromJson(json, new TypeReference<>() {});
        return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
    }

    @Named("stringObjectMapToJson")
    public String stringObjectMapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String json = JsonKit.toJson(map);
        if (json == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid auth config json");
        }
        return json;
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
        List<Integer> parsed = JsonKit.fromJson(json, new TypeReference<>() {});
        return (parsed == null || parsed.isEmpty()) ? List.of(10, 30, 50, 100) : List.copyOf(parsed);
    }

    @Named("stepsToJson")
    public String stepsToJson(List<Integer> steps) {
        String json = JsonKit.toJson(steps);
        if (json == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "steps json serialize failed");
        }
        return json;
    }
}
