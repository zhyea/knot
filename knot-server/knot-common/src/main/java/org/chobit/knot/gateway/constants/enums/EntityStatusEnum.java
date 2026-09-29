package org.chobit.knot.gateway.constants.enums;

/**
 * 实体通用生命周期状态，已从 ks_enum_configs（status 字典）迁出，
 * 通过 /api/common/enums 以 EntityStatusEnum 键下发前端。
 */
public enum EntityStatusEnum implements EnumOption {
    ENABLED("ENABLED", "启用"),
    DISABLED("DISABLED", "禁用"),
    ACTIVE("ACTIVE", "生效"),
    INACTIVE("INACTIVE", "停用"),
    /** 生命周期删除（如计费规则），查询侧排除 */
    DELETED("DELETED", "已删除");

    private final String code;
    private final String label;

    EntityStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs status 字典标签） */
    @Override
    public String label() {
        return label;
    }
}
