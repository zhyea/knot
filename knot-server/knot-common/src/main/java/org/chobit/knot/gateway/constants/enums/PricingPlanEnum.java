package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 进阶定价方案：计费模式（BillingModeEnum，决定“量”）之上的“价”层。
 * 不改变用量提取，只决定单价如何确定；新增方案只扩展本枚举与对应策略，不再扩展 BillingModeEnum。
 *
 * <p>第一阶段每版本仅允许一个方案；未来组合时将单值升级为有序方案列表
 * （固定顺序：阶梯选择 -> 高低峰倍率）。
 */
public enum PricingPlanEnum implements EnumOption {

    /** 基础价格直出（默认方案），支持全部计费模式 */
    FIXED("固定价"),

    /** 阶梯价：按约定用量（首期=本次请求总 Token）命中价格档位；首期仅 TOKEN */
    TIERED("阶梯价", BillingModeEnum.TOKEN),

    /**
     * 高低峰价：按发生时点所处相位打折，相位判定见 {@code PeakOffPeakResolver}；首期仅 TOKEN。
     * 已随阶段三落地与本方案一并开放（原 `this != PEAK_OFF_PEAK` 的排除条件已移除）。
     */
    PEAK_OFF_PEAK("高低峰价", BillingModeEnum.TOKEN);

    private final String label;
    private final List<BillingModeEnum> supportedBillingModes;

    PricingPlanEnum(String label, BillingModeEnum... supportedBillingModes) {
        this.label = label;
        this.supportedBillingModes = List.of(supportedBillingModes);
    }

    /** 支持该方案的计费模式（FIXED 未显式声明时视为全模式支持） */
    public List<BillingModeEnum> supportedBillingModes() {
        return supportedBillingModes.isEmpty()
                ? Arrays.asList(BillingModeEnum.values())
                : supportedBillingModes;
    }

    /** 该方案是否可用于指定计费模式 */
    public boolean supports(BillingModeEnum mode) {
        return mode != null && supportedBillingModes().contains(mode);
    }

    /** 是否已对外开放（能力矩阵下发与保存校验用；阶段三后三个方案全部开放） */
    public boolean isAvailable() {
        return true;
    }

    public String code() {
        return name();
    }

    /** 前端展示名（原 ks_enum_configs billing_pricing_plan 字典标签） */
    @Override
    public String label() {
        return label;
    }

    public static PricingPlanEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return FIXED;
        }
        String normalized = code.trim().toUpperCase();
        for (PricingPlanEnum plan : values()) {
            if (plan.name().equals(normalized)) {
                return plan;
            }
        }
        return null;
    }
}
