package org.chobit.knot.common.constants.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 计费模式：决定“量”——用量如何提取、计费明细有哪些；阶梯/高低峰等“价”的策略属于
 * {@link PricingPlanEnum}。除 code 外还承载该模式可用的计费单位与默认取值。
 */
public enum BillingModeEnum implements EnumOption {
    TOKEN("TOKEN", "Token", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS),
            BillingUnitEnum.ONE_M_TOKENS),
    REQUEST("REQUEST", "请求", units(BillingUnitEnum.PER_REQUEST), BillingUnitEnum.PER_REQUEST),
    IMAGE("IMAGE", "图片", units(BillingUnitEnum.PER_IMAGE), BillingUnitEnum.PER_IMAGE),
    AUDIO("AUDIO", "音频", units(BillingUnitEnum.PER_SECOND), BillingUnitEnum.PER_SECOND),
    /**
     * 视频：默认按秒（{@code PER_SECOND}），token 三档供「视频模型按 token 计价」的厂商使用。
     * 量纲由所选单位声明：{@code 1M_TOKENS} 取 token 量，{@code PER_SECOND} 取秒。
     * 分辨率与「是否含视频输入」在两种量纲下都是分档键，不是计费量。
     */
    VIDEO("VIDEO", "视频", units(BillingUnitEnum.PER_SECOND, BillingUnitEnum.PER_TOKEN,
            BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS), BillingUnitEnum.PER_SECOND),
    EMBEDDING("EMBEDDING", "Embedding", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS),
            BillingUnitEnum.ONE_M_TOKENS),
    FREE("FREE", "免费", units(BillingUnitEnum.ONE_K_TOKENS), BillingUnitEnum.ONE_K_TOKENS),
    CUSTOM("CUSTOM", "自定义", units(BillingUnitEnum.PER_TOKEN, BillingUnitEnum.ONE_K_TOKENS, BillingUnitEnum.ONE_M_TOKENS,
            BillingUnitEnum.PER_REQUEST, BillingUnitEnum.PER_IMAGE, BillingUnitEnum.PER_SECOND),
            BillingUnitEnum.ONE_M_TOKENS);

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
     * 计费单位的唯一归一化出口：空白、非法编码、或本模式不支持时，一律取 {@link #defaultUnit()} 的编码。
     * 「不支持即纠正为模式默认」而非报错，与管理端表单回置默认单位的行为一致。
     *
     * @param unitCode 待归一化的单位编码，允许 null / 空白 / 大小写混杂
     * @return 该模式下一定合法的 {@link BillingUnitEnum} 编码
     */
    public String normalizeUnit(String unitCode) {
        BillingUnitEnum unit = BillingUnitEnum.fromCode(unitCode);
        return unit != null && supportsUnit(unit.code()) ? unit.code() : defaultUnit.code();
    }

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
