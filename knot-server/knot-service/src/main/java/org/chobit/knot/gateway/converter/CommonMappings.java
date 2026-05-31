package org.chobit.knot.gateway.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
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
 * MapStruct 鍏变韩杞崲鏂规硶锛歴tatus鈫攅nabled銆丣SON鈫擮bject
 */
@Component
public class CommonMappings {

    // ==================== status 鈫?enabled ====================

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("statusToEnabled")
    public boolean statusToEnabled(String status) {
        return EntityStatusEnum.ENABLED.code().equals(status);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("enabledToStatus")
    public String enabledToStatus(boolean enabled) {
        return enabled ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code();
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("billingStatusToEnabled")
    public boolean billingStatusToEnabled(String status) {
        if (status == null) {
            return false;
        }
        return EntityStatusEnum.ACTIVE.code().equalsIgnoreCase(status) || EntityStatusEnum.ENABLED.code().equalsIgnoreCase(status);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("billingEnabledToStatus")
    public String billingEnabledToStatus(boolean enabled) {
        return enabled ? EntityStatusEnum.ACTIVE.code() : EntityStatusEnum.INACTIVE.code();
    }

    // ==================== JSON 鈫?RateLimitPolicy ====================

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("jsonToRateLimit")
    public RateLimitPolicy jsonToRateLimit(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonKit.fromJson(json, RateLimitPolicy.class);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("rateLimitToJson")
    public String rateLimitToJson(RateLimitPolicy policy) {
        if (policy == null) return null;
        String json = JsonKit.toJson(policy);
        if (json == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid rate limit policy json");
        }
        return json;
    }

    // ==================== JSON 鈫?QuotaPolicy ====================

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("jsonToQuota")
    public QuotaPolicy jsonToQuota(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonKit.fromJson(json, QuotaPolicy.class);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("quotaToJson")
    public String quotaToJson(QuotaPolicy policy) {
        if (policy == null) return null;
        String json = JsonKit.toJson(policy);
        if (json == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid quota policy json");
        }
        return json;
    }

    // ==================== JSON 鈫?Map (璁よ瘉閰嶇疆绛? ====================

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("jsonToStringObjectMap")
    public Map<String, Object> jsonToStringObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> parsed = JsonKit.fromJson(json, new TypeReference<>() {});
        return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
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

    // ==================== Long id 鈫?Alert ID (ALERT- 鍓嶇紑) ====================

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("idToAlertId")
    public String idToAlertId(Long id) {
        return id == null ? "ALERT-0" : "ALERT-" + id;
    }

    // ==================== String 鈫?Long (owner 瀛楁) ====================

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("stringToLong")
    public Long stringToLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    // ==================== JSON 鈫?List<Integer> (steps) ====================

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("jsonToSteps")
    public List<Integer> jsonToSteps(String json) {
        if (json == null || json.isBlank()) return List.of(10, 30, 50, 100);
        List<Integer> parsed = JsonKit.fromJson(json, new TypeReference<>() {});
        return (parsed == null || parsed.isEmpty()) ? List.of(10, 30, 50, 100) : List.copyOf(parsed);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Named("stepsToJson")
    public String stepsToJson(List<Integer> steps) {
        String json = JsonKit.toJson(steps);
        if (json == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "steps json serialize failed");
        }
        return json;
    }
}
