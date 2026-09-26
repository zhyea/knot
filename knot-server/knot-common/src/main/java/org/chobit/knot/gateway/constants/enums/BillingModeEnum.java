package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 计费模式。除 code 外，还承载该模式可用的计费单位与默认取值，
 * 使「模式 -> 单位/价格项」这条业务规则只有一份定义（此前只存在于前端 unitsByMode / defaultsByMode）。
 */
public enum BillingModeEnum {
    TOKEN("TOKEN", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS),
            BillingUnitEnum.ONE_K_TOKENS, "INPUT_TOKEN"),
    REQUEST("REQUEST", units(BillingUnitEnum.PER_REQUEST), BillingUnitEnum.PER_REQUEST, "REQUEST"),
    IMAGE("IMAGE", units(BillingUnitEnum.PER_IMAGE), BillingUnitEnum.PER_IMAGE, "IMAGE"),
    AUDIO("AUDIO", units(BillingUnitEnum.PER_MINUTE), BillingUnitEnum.PER_MINUTE, "AUDIO_MINUTE"),
    VIDEO("VIDEO", units(BillingUnitEnum.PER_SECOND), BillingUnitEnum.PER_SECOND, "VIDEO_SECOND"),
    EMBEDDING("EMBEDDING", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS),
            BillingUnitEnum.ONE_K_TOKENS, "EMBEDDING_TOKEN"),
    TIERED("TIERED", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS),
            BillingUnitEnum.ONE_K_TOKENS, "TIERED_USAGE"),
    FREE("FREE", units(BillingUnitEnum.ONE_K_TOKENS), BillingUnitEnum.ONE_K_TOKENS, "FREE"),
    CUSTOM("CUSTOM", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS,
            BillingUnitEnum.PER_REQUEST, BillingUnitEnum.PER_IMAGE, BillingUnitEnum.PER_MINUTE, BillingUnitEnum.PER_SECOND),
            BillingUnitEnum.ONE_K_TOKENS, "CUSTOM");

    private final String code;
    private final List<BillingUnitEnum> supportedUnits;
    private final BillingUnitEnum defaultUnit;
    private final String defaultItemType;

    BillingModeEnum(String code, List<BillingUnitEnum> supportedUnits, BillingUnitEnum defaultUnit, String defaultItemType) {
        this.code = code;
        this.supportedUnits = List.copyOf(supportedUnits);
        this.defaultUnit = defaultUnit;
        this.defaultItemType = defaultItemType;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
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

    /** 该模式的默认价格项（取值来自 ks_enum_configs 的 billing_item_type 分类） */
    public String defaultItemType() {
        return defaultItemType;
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
