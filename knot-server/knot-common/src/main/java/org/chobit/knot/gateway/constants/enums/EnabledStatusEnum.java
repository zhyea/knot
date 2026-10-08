package org.chobit.knot.gateway.constants.enums;

import java.util.List;

/**
 * 二态可用状态：全项目唯一的二态持久化枚举。
 *
 * <p>历史 {@code ACTIVE / INACTIVE}（生效 / 停用）与 {@code ENABLED / DISABLED}（启用 / 禁用）
 * 在本枚举中归一为 1 / 0，页面文案由字段语义决定，不再建立第二套二态枚举。
 * 删除语义不属于本枚举，一律走 {@code is_deleted}。</p>
 *
 * <p>{@code DISABLED = 0} 是合法值：前后端禁止用 truthy 判断代替显式比较。</p>
 */
public enum EnabledStatusEnum implements NumericEnumOption {
    ENABLED(1, "启用"),
    DISABLED(0, "禁用");

    private final int code;
    private final String label;

    EnabledStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static EnabledStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    public static EnabledStatusEnum requireCode(Integer code, String errorMessage) {
        return NumericEnumOption.requireCode(values(), code, errorMessage);
    }

    /** 全部状态 code，供查询下拉使用 */
    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }

    /** boolean -> code */
    public static int codeOf(boolean enabled) {
        return enabled ? ENABLED.code() : DISABLED.code();
    }

    /** code -> boolean；null 与非 1 均视为未启用 */
    public static boolean isEnabled(Integer code) {
        return ENABLED.code() == (code == null ? -1 : code);
    }

    /**
     * 查询参数归一："1"/"0" 直接映射；历史字符串值（ENABLED/ACTIVE/DISABLED/INACTIVE）
     * 在数字化过渡期容忍并归一；其余值返回 null（不过滤）。
     */
    public static Integer parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        if ("1".equals(value) || "ENABLED".equalsIgnoreCase(value) || "ACTIVE".equalsIgnoreCase(value)) {
            return ENABLED.code();
        }
        if ("0".equals(value) || "DISABLED".equalsIgnoreCase(value) || "INACTIVE".equalsIgnoreCase(value)) {
            return DISABLED.code();
        }
        return null;
    }
}
