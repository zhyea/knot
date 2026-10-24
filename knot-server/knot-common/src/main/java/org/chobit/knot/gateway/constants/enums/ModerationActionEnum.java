package org.chobit.knot.gateway.constants.enums;

/**
 * 敏感词命中后的处置动作（全局唯一一份策略，无资源级绑定）。
 *
 * <p>code 与枚举名一致，落库与配置均使用该 code。</p>
 *
 * <p>2026-10-24（robin 拍板）：脱敏动作 MASK 已移除——「宁可不做，不可配了不生效」。
 * 移除而非保留降级，是因为降级会让配置层出现「选了 MASK 实际按 FLAG 跑」的假成功；
 * 现在写 {@code MASK} 会启动失败（见 {@code PluginProperties} 的启动校验），
 * 配置错误在最早期显式暴露，不会以静默兜底的形式活到生产。</p>
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
