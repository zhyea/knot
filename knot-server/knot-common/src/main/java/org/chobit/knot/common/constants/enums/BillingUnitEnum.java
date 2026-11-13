package org.chobit.knot.common.constants.enums;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

import java.util.Arrays;

/**
 * 计费单位：通过 {@code /api/enum-options} 以 {@code BillingUnitEnum} 键下发前端。
 * 某计费模式可用的单位集合由 {@link BillingModeEnum#supportedUnits()} 约束，本枚举只定义取值本身。
 */
public enum BillingUnitEnum implements EnumOption {
    ONE_K_TOKENS("1K_TOKENS", "每千 Token 单价"),
    ONE_M_TOKENS("1M_TOKENS", "每百万 Token 单价"),
    PER_TOKEN("PER_TOKEN", "每 Token 单价"),
    PER_REQUEST("PER_REQUEST", "每次单价"),
    PER_IMAGE("PER_IMAGE", "每张单价"),
    PER_SECOND("PER_SECOND", "每秒单价"),
    CUSTOM("CUSTOM", "自定义");

    private final String code;
    private final String label;

    BillingUnitEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

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
     * 单个计费单位对应的数量基数（如 {@code 1K_TOKENS=1000}）：金额 = 用量 / unitSize * 单价 的除数。
     * 所有基数都是 10 的幂（1 / 1e3 / 1e6），金额公式恒定除尽。
     * 新增枚举必须显式给出基数，未覆盖时运行期直接抛异常。
     */
    public int unitSize() {
        return switch (this) {
            case ONE_K_TOKENS -> 1_000;
            case ONE_M_TOKENS -> 1_000_000;
            case PER_TOKEN, PER_REQUEST, PER_IMAGE, PER_SECOND, CUSTOM -> 1;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "billing unit not covered by unitSize: " + code);
        };
    }

    /**
     * 该单位是否是「按 token 计」的量纲；量纲由单位本身声明，不需要另设 {@code quantitySource} 字段。
     * VIDEO 模式默认按秒，按 token 计价的厂商选 {@code 1M_TOKENS} 时，计算器据此改取 token 量。
     */
    public boolean tokenBased() {
        return this == PER_TOKEN || this == ONE_K_TOKENS || this == ONE_M_TOKENS;
    }

    /**
     * 按编码解析计费单位并返回数量基数，无法识别的编码直接拒绝。
     */
    public static int unitSizeOf(String code) {
        BillingUnitEnum unit = fromCode(code);
        if (unit == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "unsupported billing unit: " + code);
        }
        return unit.unitSize();
    }

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
