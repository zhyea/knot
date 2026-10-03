package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;

/**
 * 路由连通性测试结果：从 ks_enum_configs 的通用 status 字典中拆分出来的领域枚举，
 * 通过 /api/common/enums 以 RoutingTestStatusEnum 键下发前端。
 *
 * <p>该领域历史上用 FAILED 表示未通过，与操作日志的 FAILURE 不是同一套取值，故独立成枚举。</p>
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

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
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
