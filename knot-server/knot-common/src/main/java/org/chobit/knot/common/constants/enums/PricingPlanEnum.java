package org.chobit.knot.common.constants.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 进阶定价方案：{@link BillingModeEnum}（决定“量”）之上的“价”层，不改变用量提取，只决定单价如何确定；
 * 新增方案只扩展本枚举与对应策略。每版本仅允许一个方案，组合时按有序列表叠加（阶梯 → 高低峰倍率）。
 */
public enum PricingPlanEnum implements EnumOption {

    /** 基础价格直出（默认方案），支持全部计费模式 */
    FIXED("固定价"),

    /**
     * 阶梯价：按约定用量命中价格档位（默认基准为本次请求总 Token，媒体模式为该模式的计费量：
     * IMAGE = 张数、AUDIO/VIDEO = 秒数）；整笔落一档，或按 {@code tierMode=PROGRESSIVE} 逐单位累加。
     */
    TIERED("阶梯价", BillingModeEnum.TOKEN, BillingModeEnum.IMAGE, BillingModeEnum.AUDIO, BillingModeEnum.VIDEO),

    /**
     * 高低峰价：按发生时点所处相位打折，相位判定见 {@code PeakOffPeakResolver}。
     */
    PEAK_OFF_PEAK("高低峰价", BillingModeEnum.TOKEN, BillingModeEnum.IMAGE, BillingModeEnum.AUDIO,
            BillingModeEnum.VIDEO),

    /**
     * 厂商定制计费规则（进阶定价方案家族中的特例，旗舰 = MiniMax）；仅 VIDEO 模式可用。
     * 口径见 {@code VideoBillingModeCalculator#calculateVendorMiniMax}：输出秒 × 档位输出价 + 输入秒 × 档位输入价
     * （默认回落同档位输出价）+ max(0, 图片张数 − 免费张数) × 每张固定价；音频输入免费，无阶梯/错峰。
     * 方案层只按档位键取单价（语义同 {@link #FIXED}），多分量编排在计算器。
     */
    VENDOR_MINIMAX("厂商定制(MiniMax)", BillingModeEnum.VIDEO),

    /**
     * 维度分档价：按请求属性分档取价（分辨率 × 是否输出声音 × 有无输入视频），仅 VIDEO 模式可用。
     * 每个档位按 {@code 档位:REFERENCE:AUDIO} → {@code 档位:REFERENCE} → {@code 档位:AUDIO} → {@code 档位}
     * 的顺序逐级去尾降级；「有无输入视频」与「有无参考视频」同义，参考图走图片张数口径。
     * 取价语义同 {@link #FIXED}（无阶梯、无相位倍率），但维度键拼错或空档位名在保存期直接拒绝。
     */
    DIMENSION_TIERED("维度分档价", BillingModeEnum.VIDEO);

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

    /** 是否已对外开放（能力矩阵下发与保存校验用；三个方案全部开放） */
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
