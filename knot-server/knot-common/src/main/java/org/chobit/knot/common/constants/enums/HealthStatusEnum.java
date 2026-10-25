package org.chobit.knot.common.constants.enums;

import java.util.List;

/**
 * 健康检查状态（{@code /api/health}）。
 *
 * <p><b>本期只建枚举 + 单测，不注册 {@code EnumOptionRegistry}、不改接口返回。</b>
 * 待调用方确认后再切换：切换时只输出数字 {@code statusCode}，不保留旧字符串读法。</p>
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
