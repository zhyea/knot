package org.chobit.knot.common.constants.enums;

import java.util.List;

/**
 * 二态可用状态：1 = 启用 / 0 = 禁用，页面文案由字段语义决定；全项目唯一的二态持久化枚举。
 * 删除语义不属于本枚举，一律走 {@code is_deleted}；{@code DISABLED = 0} 是合法值，禁止用 truthy 判断。
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
     * 查询参数归一：仅接受数字 "1"/"0"；其余值返回 null（不过滤）。
     */
    public static Integer parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        if ("1".equals(value)) {
            return ENABLED.code();
        }
        if ("0".equals(value)) {
            return DISABLED.code();
        }
        return null;
    }
}
