package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 计费模式：决定“量”——用量如何提取、计费明细有哪些。
 * 阶梯/高低峰等“价”的策略属于进阶定价方案（PricingPlanEnum），不再作为模式存在于本枚举。
 * 除 code 外，还承载该模式可用的计费单位与默认取值，
 * 使「模式 -> 单位」这条业务规则只有一份定义（此前只存在于前端 unitsByMode）。
 */
public enum BillingModeEnum implements EnumOption {
    TOKEN("TOKEN", "Token", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS),
            BillingUnitEnum.ONE_K_TOKENS),
    REQUEST("REQUEST", "请求", units(BillingUnitEnum.PER_REQUEST), BillingUnitEnum.PER_REQUEST),
    IMAGE("IMAGE", "图片", units(BillingUnitEnum.PER_IMAGE), BillingUnitEnum.PER_IMAGE),
    AUDIO("AUDIO", "音频", units(BillingUnitEnum.PER_MINUTE), BillingUnitEnum.PER_MINUTE),
    VIDEO("VIDEO", "视频", units(BillingUnitEnum.PER_SECOND), BillingUnitEnum.PER_SECOND),
    EMBEDDING("EMBEDDING", "Embedding", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS),
            BillingUnitEnum.ONE_K_TOKENS),
    FREE("FREE", "免费", units(BillingUnitEnum.ONE_K_TOKENS), BillingUnitEnum.ONE_K_TOKENS),
    CUSTOM("CUSTOM", "自定义", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS,
            BillingUnitEnum.PER_REQUEST, BillingUnitEnum.PER_IMAGE, BillingUnitEnum.PER_MINUTE, BillingUnitEnum.PER_SECOND),
            BillingUnitEnum.ONE_K_TOKENS);

    private final String code;
    private final String label;
    private final List<BillingUnitEnum> supportedUnits;
    private final BillingUnitEnum defaultUnit;

    BillingModeEnum(String code, String label, List<BillingUnitEnum> supportedUnits, BillingUnitEnum defaultUnit) {
        this.code = code;
        this.label = label;
        this.supportedUnits = List.copyOf(supportedUnits);
        this.defaultUnit = defaultUnit;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs billing_mode 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /** 该模式支持的计费单位 */
    public List<BillingUnitEnum> supportedUnits() {
        return supportedUnits;
    }

    public List<String> supportedUnitCodes() {
        return supportedUnits.stream().map(BillingUnitEnum::code).toList();
    }

    /** 该模式的默认计费单位 */
    public BillingUnitEnum defaultUnit() {
        return defaultUnit;
    }

    /** 单位是否被该模式支持 */
    public boolean supportsUnit(String unitCode) {
        BillingUnitEnum unit = BillingUnitEnum.fromCode(unitCode);
        return unit != null && supportedUnits.contains(unit);
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static BillingModeEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private static List<BillingUnitEnum> units(BillingUnitEnum... items) {
        return List.of(items);
    }
}
