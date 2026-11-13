package org.chobit.knot.common.constants.enums;

import com.fasterxml.jackson.databind.JsonNode;
import org.chobit.knot.common.constants.ProviderCredentialFields;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Supported provider credential types. The code is persisted as a string; the enum declares the
 * supported values, their labels and the reference to each type's credential field definitions.
 * {@code requiredFields} 是认证字段定义数组（{@link JsonNode}），一处定义同时喂前端动态渲染表单
 * 与后端保存校验（{@link #requiredFieldNames()}）；字段定义本体集中在 {@link ProviderCredentialFields}。
 */
public enum ProviderCredentialTypeEnum {
    API_KEY("api-key", "ApiKey", ProviderCredentialFields.API_KEY_FIELDS),
    AWS_AUTH("aws-auth", "AWS 认证", ProviderCredentialFields.AWS_AUTH_FIELDS),
    GEMINI_AUTH("gemini-auth", "gemini 认证", ProviderCredentialFields.GEMINI_AUTH_FIELDS),
    AK_SK("ak-sk", "AK&SK", ProviderCredentialFields.AK_SK_FIELDS),
    CUSTOM("custom", "自定义", ProviderCredentialFields.CUSTOM_FIELDS);

    private final String code;
    private final String label;
    private final JsonNode requiredFields;

    ProviderCredentialTypeEnum(String code, String label, JsonNode requiredFields) {
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

    /**
     * 认证字段定义数组（节点结构见 {@link ProviderCredentialFields}）；{@code CUSTOM} 返回空数组，
     * 且一律返回深拷贝，调用方修改不影响共享常量。
     */
    public JsonNode requiredFields() {
        return requiredFields.deepCopy();
    }

    /**
     * 保存时必须有值的字段名，按声明顺序；节点未显式写 {@code required} 时按必填处理。
     */
    public List<String> requiredFieldNames() {
        List<String> names = new ArrayList<>();
        for (JsonNode field : requiredFields) {
            if (field.path("required").asBoolean(true)) {
                names.add(field.path("name").asText());
            }
        }
        return List.copyOf(names);
    }

    /**
     * 按持久化 code 解析认证类型；未声明（null / 空白）或未知值直接抛错——
     * credential_type 是 NOT NULL 列，且创建入口必填，不做空值默认。
     */
    public static ProviderCredentialTypeEnum fromCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("credential type is required");
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(normalized)
                        || item.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported credential type: " + value));
    }
}
