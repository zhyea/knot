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

    /**
     * Executes the public operation. Executes the public operation.
     */
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
     * 写入/校验入口的归一：空白或未知值回退调用方指定的类型。
     *
     * <p>刻意<b>要求显式传 fallback</b>——「空当作 MODEL」是路由目标的业务默认，不该藏在枚举里
     * 静默生效；换一个调用方（比如调试目标）可能希望未知值直接报错。</p>
     */
    public static RouteTargetTypeEnum ofOrDefault(String code, RouteTargetTypeEnum fallback) {
        RouteTargetTypeEnum value = fromCode(code);
        return value == null ? fallback : value;
    }

    /**
     * 大小写不敏感的 code 比较（管理面与低频路径用）。
     * 网关热路径（{@code RoutingResolver}）保持 {@code MODEL.code().equals(...)}，
     * 避免每个目标逐次做 trim + toUpperCase 的归一开销。
     */
    public boolean matches(String code) {
        return code != null && code.trim().equalsIgnoreCase(this.code);
    }
}
