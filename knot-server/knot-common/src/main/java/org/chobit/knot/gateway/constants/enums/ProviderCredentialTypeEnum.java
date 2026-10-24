package org.chobit.knot.gateway.constants.enums;

import com.fasterxml.jackson.databind.JsonNode;
import org.chobit.knot.gateway.constants.ProviderCredentialFields;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Supported provider credential types. The code is persisted as a string,
 * while the enum keeps the supported values and references the credential
 * field definitions.
 *
 * <p>{@code requiredFields} 不是一串字段名，而是**认证字段定义数组**（Jackson {@link JsonNode}），
 * 一处定义同时喂给两侧：
 * <ul>
 *   <li>前端 → 动态渲染表单（label 作标签、type 决定控件、options 作下拉选项、default 预填）；</li>
 *   <li>后端 → 保存校验（{@link #requiredFieldNames()} 取 required=true 的字段名）。</li>
 * </ul>
 *
 * <p><b>字段定义本身不在本枚举维护</b>：常量集中在 {@link ProviderCredentialFields}，
 * 同名字段（accessKey/secretKey）可被多个认证类型复用；本枚举只声明 code / label 与
 * 「类型 → 字段集合」的引用关系。
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
     * 认证字段定义数组（节点结构见 {@link ProviderCredentialFields}）；CUSTOM 返回空数组。
     *
     * <p>返回深拷贝：常量被多个认证类型共享，调用方改坏了会影响全局。
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
