package org.chobit.knot.common.constants.enums;

/**
 * 路由目标类型，已从硬编码/字典迁出，
 * 通过 /api/enum-options 以 RouteTargetTypeEnum 键下发前端。
 */
public enum RouteTargetTypeEnum implements EnumOption {
    MODEL("MODEL", "模型"),
    MODEL_POOL("MODEL_POOL", "模型池");

    private final String code;
    private final String label;

    RouteTargetTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    /** 前端展示名 */
    @Override
    public String label() {
        return label;
    }

    /**
     * 按 code 解析（大小写不敏感）；空白或未知返回 {@code null}，由调用方决定如何处理。
     */
    public static RouteTargetTypeEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (RouteTargetTypeEnum value : values()) {
            if (value.code.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 写入/校验入口的归一：空白或未知值回退调用方显式指定的类型（{@code fallback}）。
     */
    public static RouteTargetTypeEnum ofOrDefault(String code, RouteTargetTypeEnum fallback) {
        RouteTargetTypeEnum value = fromCode(code);
        return value == null ? fallback : value;
    }

    /**
     * 大小写不敏感的 code 比较，仅用于管理面与低频路径；网关热路径用 {@code MODEL.code().equals(...)} 直接比较。
     */
    public boolean matches(String code) {
        return code != null && code.trim().equalsIgnoreCase(this.code);
    }
}
