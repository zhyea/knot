package org.chobit.knot.gateway.constants.enums;

import java.util.List;

/**
 * 敏感词命中后的处置动作（全局唯一一份策略，无资源级绑定）。
 *
 * <p>code 与枚举名一致，落库与配置均使用该 code。
 * 本期只建枚举 + 单测，<b>不注册 {@code EnumOptionRegistry}、不改 {@code /api/common/enums} 返回</b>
 * （对齐 {@code HealthStatusEnum} 口径；词库管理 UI 上线时再注册）。</p>
 */
public enum ModerationActionEnum implements EnumOption {
    BLOCK("BLOCK", "拦截：请求打回 400 / 整包响应不交付"),
    MASK("MASK", "脱敏：命中词替换为掩码后放行（需原文 offset 映射，M2 实现）"),
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
     * Builds the target value from the source input. Executes the public operation.
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

    /**
     * 配置值解析：非法值回退默认动作（FLAG），配置写错不阻断启动。
     */
    public static ModerationActionEnum ofOrDefault(String code, ModerationActionEnum fallback) {
        ModerationActionEnum value = fromCode(code);
        return value == null ? fallback : value;
    }

    /** 全部动作 code，供下拉使用 */
    public static List<String> codes() {
        return java.util.Arrays.stream(values()).map(ModerationActionEnum::code).toList();
    }
}
