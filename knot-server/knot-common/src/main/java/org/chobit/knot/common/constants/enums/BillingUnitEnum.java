package org.chobit.knot.common.constants.enums;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

import java.util.Arrays;

/**
 * 计费单位：已从 ks_enum_configs（billing_unit 字典）迁出，
 * 通过 /api/enum-options 以 BillingUnitEnum 键下发前端。
 * 某个计费模式可用的单位集合由 {@link BillingModeEnum#supportedUnits()} 约束，本枚举只定义取值本身。
 */
public enum BillingUnitEnum implements EnumOption {
    ONE_K_TOKENS("1K_TOKENS", "千 Token"),
    ONE_M_TOKENS("1M_TOKENS", "百万 Token"),
    PER_TOKEN("PER_TOKEN", "单 Token"),
    PER_REQUEST("PER_REQUEST", "按请求"),
    PER_IMAGE("PER_IMAGE", "按图片"),
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
     * 单个计费单位对应的数量基数（如 1K_TOKENS=1000）。
     * 金额 = 用量 / unitSize * 单价。新增枚举必须显式给出基数，
     * 未覆盖时在运行期直接抛异常，避免静默按错误基数计费造成资损。
     *
     * <p>⭐ 所有基数都是 10 的幂（1 / 1e3 / 1e6）：金额 = 用量 × 单价 ÷ 基数
     * 恒定除尽，不终止除法在这套单位体系里不可能出现。
     * PER_MINUTE（60 秒）已于 2026-10-17 退役——它是唯一的非 10 的幂基数，
     * 会逼出「秒 ÷ 60」的不终止除法；退役后 audio 与 video 统一按秒计费。
     * 若未来确需按分钟计费，必须在配置期把单价折成每秒价，不要让 60 进金额公式
     * （设计纪要见 docs/design/routing-test-drawer-billing-verify-design.md §6）。</p>
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
     * 按编码解析计费单位并返回数量基数，无法识别的编码直接拒绝。
     */
    public static int unitSizeOf(String code) {
        BillingUnitEnum unit = fromCode(code);
        if (unit == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "unsupported billing unit: " + code);
        }
        return unit.unitSize();
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
