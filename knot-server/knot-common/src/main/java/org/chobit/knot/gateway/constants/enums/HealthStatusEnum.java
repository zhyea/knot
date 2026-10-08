package org.chobit.knot.gateway.constants.enums;

import java.util.List;

/**
 * 健康检查状态（{@code /api/health}）。
 *
 * <p><b>本期只建枚举 + 单测，不注册 {@code EnumOptionRegistry}、不改接口返回。</b>
 * 接口仍返回既有字符串；{@code ok} 保留为兼容读法。
 * 待调用方确认后再切换，切换时同时提供数字 {@code statusCode} 与 deprecated 字符串 {@code status}。</p>
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
