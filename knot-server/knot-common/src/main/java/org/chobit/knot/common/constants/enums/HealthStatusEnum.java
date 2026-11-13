package org.chobit.knot.common.constants.enums;

import java.util.List;

/**
 * 健康检查状态（{@code /api/health}）：{@code UP} / {@code DEGRADED} / {@code DOWN} 对应 1~3，
 * 不注册 {@code EnumOptionRegistry}。
 */
public enum HealthStatusEnum implements NumericEnumOption {
    UP(1, "正常"),
    DEGRADED(2, "降级"),
    DOWN(3, "故障");

    private final int code;
    private final String label;

    HealthStatusEnum(int code, String label) {
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

    public static HealthStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    public static HealthStatusEnum requireCode(Integer code, String errorMessage) {
        return NumericEnumOption.requireCode(values(), code, errorMessage);
    }

    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }
}
