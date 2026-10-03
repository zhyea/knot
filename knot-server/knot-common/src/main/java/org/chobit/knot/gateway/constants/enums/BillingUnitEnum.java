package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.Arrays;

/**
 * 计费单位：已从 ks_enum_configs（billing_unit 字典）迁出，
 * 通过 /api/common/enums 以 BillingUnitEnum 键下发前端。
 * 某个计费模式可用的单位集合由 {@link BillingModeEnum#supportedUnits()} 约束，本枚举只定义取值本身。
 */
public enum BillingUnitEnum implements EnumOption {
    ONE_K_TOKENS("1K_TOKENS", "千 Token"),
    ONE_M_TOKENS("1M_TOKENS", "百万 Token"),
    PER_TOKEN("PER_TOKEN", "单 Token"),
    PER_REQUEST("PER_REQUEST", "按请求"),
    PER_IMAGE("PER_IMAGE", "按图片"),
    PER_MINUTE("PER_MINUTE", "按分钟"),
    PER_SECOND("PER_SECOND", "按秒"),
    CUSTOM("CUSTOM", "自定义");

    private final String code;
    private final String label;

    BillingUnitEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs billing_unit 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static BillingUnitEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /**
     * 校验计费单位编码并返回规范大写形式，非法值直接拒绝。
     */
    public static String requireCode(String code, String errorMessage) {
        BillingUnitEnum unit = fromCode(code);
        if (unit == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return unit.code();
    }
}
