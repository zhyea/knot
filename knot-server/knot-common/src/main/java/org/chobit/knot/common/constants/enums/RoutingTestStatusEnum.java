package org.chobit.knot.common.constants.enums;

import java.util.Arrays;

/**
 * 路由连通性测试结果：通过 {@code /api/enum-options} 以 {@code RoutingTestStatusEnum} 键下发前端。
 * 未通过取值为 {@code FAILED}，与 {@link OperationLogStatusEnum} 的 {@code FAILURE} 不是同一套取值。
 */
public enum RoutingTestStatusEnum implements EnumOption {
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;
    private final String label;

    RoutingTestStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    /** 前端展示名 */
    @Override
    public String label() {
        return label;
    }

    public static RoutingTestStatusEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
