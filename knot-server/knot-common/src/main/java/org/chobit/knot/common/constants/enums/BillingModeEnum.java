package org.chobit.knot.common.constants.enums;

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
            BillingUnitEnum.ONE_M_TOKENS),
    REQUEST("REQUEST", "请求", units(BillingUnitEnum.PER_REQUEST), BillingUnitEnum.PER_REQUEST),
    IMAGE("IMAGE", "图片", units(BillingUnitEnum.PER_IMAGE), BillingUnitEnum.PER_IMAGE),
    AUDIO("AUDIO", "音频", units(BillingUnitEnum.PER_SECOND), BillingUnitEnum.PER_SECOND),
    VIDEO("VIDEO", "视频", units(BillingUnitEnum.PER_SECOND), BillingUnitEnum.PER_SECOND),
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
     * 计费单位的唯一归一化出口：空白、非法编码、或本模式不支持时，一律取
     * {@link #defaultUnit()} 的编码。
     *
     * <p><b>为什么必须有这一个出口</b>：单位基数是金额公式的分母
     * （{@code 金额 = 用量 × 单价 ÷ 基数}），{@code 1K_TOKENS} 与 {@code PER_SECOND}
     * 之间差 1000 倍。此前「空白单位兜底成 1K_TOKENS」被抄在了三处（计算器热路径、
     * 管理端落库、DB 列 DEFAULT），而本枚举声明的却是 {@code VIDEO→PER_SECOND}、
     * {@code TOKEN→1M_TOKENS}——两个口径并存时，任何绕过管理端表单的写入
     * （脚本导入、直连改库、新接入方）都会静默拿到 1000 倍错的金额，且编译与单测全绿。
     * 现在三处一律改调本方法，口径只有这一份定义。</p>
     *
     * <p>「不支持即纠正为模式默认」而非报错：与管理端表单的行为一致
     * （前端 {@code BillingRuleFormDialog} 在单位不在 {@code supportedUnits} 内时
     * 回置为模式默认单位），前后端对同一份入参给出同一结果。</p>
     *
     * @param unitCode 待归一化的单位编码，允许 null / 空白 / 大小写混杂
     * @return 该模式下一定合法的 {@link BillingUnitEnum} 编码
     */
    public String normalizeUnit(String unitCode) {
        BillingUnitEnum unit = BillingUnitEnum.fromCode(unitCode);
        return unit != null && supportsUnit(unit.code()) ? unit.code() : defaultUnit.code();
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
