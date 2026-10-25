package org.chobit.knot.common.constants.enums;

/**
 * 敏感词审核作用方向：只审入站 / 只审出站 / 双向。
 *
 * <p>本期只建枚举 + 单测，<b>不注册 {@code EnumOptionRegistry}</b>（同 {@link ModerationActionEnum} 口径）。</p>
 */
public enum ModerationScopeEnum implements EnumOption {
    REQUEST("REQUEST", "仅入站请求"),
    RESPONSE("RESPONSE", "仅出站响应"),
    BOTH("BOTH", "双向");

    private final String code;
    private final String label;

    ModerationScopeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static ModerationScopeEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (ModerationScopeEnum value : values()) {
            if (value.code.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 配置值解析：非法值回退默认方向（BOTH）。
     */
    public static ModerationScopeEnum ofOrDefault(String code, ModerationScopeEnum fallback) {
        ModerationScopeEnum value = fromCode(code);
        return value == null ? fallback : value;
    }

    public boolean coversRequest() {
        return this == REQUEST || this == BOTH;
    }

    public boolean coversResponse() {
        return this == RESPONSE || this == BOTH;
    }
}
