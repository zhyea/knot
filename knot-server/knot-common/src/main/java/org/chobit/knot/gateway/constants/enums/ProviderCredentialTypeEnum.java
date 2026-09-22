package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Supported provider credential types. The code is persisted as a string,
 * while the enum keeps the supported values and required fields centralized.
 */
public enum ProviderCredentialTypeEnum {
    API_KEY("api-key", "ApiKey", List.of("apiKey")),
    AWS_AUTH("aws-auth", "AWS 认证", List.of("accessKey", "secretKey", "region")),
    GEMINI_AUTH("gemini-auth", "gemini 认证", List.of("project_id", "region", "account_json")),
    AK_SK("ak-sk", "AK&SK", List.of("accessKey", "secretKey")),
    CUSTOM("custom", "自定义", List.of());

    private final String code;
    private final String label;
    private final List<String> requiredFields;

    ProviderCredentialTypeEnum(String code, String label, List<String> requiredFields) {
        this.code = code;
        this.label = label;
        this.requiredFields = requiredFields;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public List<String> requiredFields() {
        return requiredFields;
    }

    /**
     * Accepts the new persisted codes and the legacy values used before the
     * credential type became explicit in the API.
     */
    public static ProviderCredentialTypeEnum fromCode(String value) {
        if (value == null || value.isBlank()) {
            return API_KEY;
        }
        String normalized = value.trim();
        if ("API_KEY".equalsIgnoreCase(normalized)) {
            return API_KEY;
        }
        if ("TOKEN".equalsIgnoreCase(normalized)) {
            return CUSTOM;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(normalized)
                        || item.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported credential type: " + value));
    }
}
