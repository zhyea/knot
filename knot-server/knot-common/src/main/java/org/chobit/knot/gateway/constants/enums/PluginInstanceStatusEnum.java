package org.chobit.knot.gateway.constants.enums;

import java.util.List;

/**
 * 插件实例状态（{@code kb_plugin_instances.status}）：DRAFT → ACTIVE → PAUSED / ARCHIVED。
 *
 * <p>实例生命周期与包状态（{@link PluginPackageStatusEnum}）是两个维度，不可混用。</p>
 */
public enum PluginInstanceStatusEnum implements NumericEnumOption {
    DRAFT(1, "草稿"),
    ACTIVE(2, "生效"),
    PAUSED(3, "暂停"),
    ARCHIVED(4, "归档");

    private final int code;
    private final String label;

    PluginInstanceStatusEnum(int code, String label) {
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

    public static PluginInstanceStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    public static PluginInstanceStatusEnum requireCode(Integer code, String errorMessage) {
        return NumericEnumOption.requireCode(values(), code, errorMessage);
    }

    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }
}
