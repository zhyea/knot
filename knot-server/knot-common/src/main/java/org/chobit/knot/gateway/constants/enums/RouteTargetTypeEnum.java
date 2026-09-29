package org.chobit.knot.gateway.constants.enums;

/**
 * 路由目标类型，已从硬编码/字典迁出，
 * 通过 /api/common/enums 以 RouteTargetTypeEnum 键下发前端。
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
}
