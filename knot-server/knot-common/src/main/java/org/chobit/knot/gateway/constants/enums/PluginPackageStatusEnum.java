package org.chobit.knot.gateway.constants.enums;

import java.util.List;

/**
 * 插件包状态（{@code kb_plugin_packages.status}）。
 *
 * <p>DEPRECATED 是「已废弃」语义，不能折叠成 DISABLED —— 废弃包仍可被已绑定实例引用。</p>
 */
public enum PluginPackageStatusEnum implements NumericEnumOption {
    ACTIVE(1, "生效"),
    DISABLED(2, "禁用"),
    DEPRECATED(3, "已废弃");

    private final int code;
    private final String label;

    PluginPackageStatusEnum(int code, String label) {
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

    public static PluginPackageStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    public static PluginPackageStatusEnum requireCode(Integer code, String errorMessage) {
        return NumericEnumOption.requireCode(values(), code, errorMessage);
    }

    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }
}
