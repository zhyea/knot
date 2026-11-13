package org.chobit.knot.common.constants.enums;

/**
 * 敏感词命中后的处置动作（全局唯一一份策略，无资源级绑定）；{@code code} 与枚举名一致，
 * 落库与配置均使用该 code。{@code MASK} 不是合法取值：写入会在启动校验时直接失败。
 */
public enum ModerationActionEnum implements EnumOption {
    BLOCK("BLOCK", "拦截：请求打回 400 / 整包响应不交付"),
    FLAG("FLAG", "标记：放行但记录命中事件（影子模式，默认）");

    private final String code;
    private final String label;

    ModerationActionEnum(String code, String label) {
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
     * 按 code 解析；null / 空串 / 未知值一律返回 {@code null}（由调用方决定如何失败，
     * 不做静默兜底——配置写错必须显式暴露）。
     */
    public static ModerationActionEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (ModerationActionEnum value : values()) {
            if (value.code.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
