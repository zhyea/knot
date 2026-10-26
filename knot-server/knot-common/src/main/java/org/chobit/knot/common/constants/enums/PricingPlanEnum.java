package org.chobit.knot.common.constants.enums;

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

    /**
     * 阶梯价：按约定用量命中价格档位（首期=本次请求总 Token）。
     *
     * <p>2026-10-25 放开到媒体模式（IMAGE / AUDIO / VIDEO）：Vidu Q2 的「首秒阶梯」
     * 正是按秒分档的现实需求，且放开的前置条件——分档价收进方案层（{@code resolveDimensionPrice}）——
     * 已随同批修复落地（此前命中分辨率价直接 return，绕过整个方案层）。</p>
     *
     * <p>媒体模式下的档位基准是该模式的计费量（IMAGE=张数、AUDIO/VIDEO=秒数），
     * 整笔落一档（默认）或逐单位累加（{@code tierMode=PROGRESSIVE}）。</p>
     */
    TIERED("阶梯价", BillingModeEnum.TOKEN, BillingModeEnum.IMAGE, BillingModeEnum.AUDIO, BillingModeEnum.VIDEO),

    /**
     * 高低峰价：按发生时点所处相位打折，相位判定见 {@code PeakOffPeakResolver}。
     *
     * <p>已随阶段三落地开放；2026-10-25 再放开到媒体模式（IMAGE / AUDIO / VIDEO）——
     * Vidu 的错峰半价是这套机制在视频域的直接对应物。⚠ 前提是分档价必须过方案层，
     * 否则「配了错峰但分辨率价不打折」会静默只对部分分量生效。</p>
     */
    PEAK_OFF_PEAK("高低峰价", BillingModeEnum.TOKEN, BillingModeEnum.IMAGE, BillingModeEnum.AUDIO,
            BillingModeEnum.VIDEO),

    /**
     * 厂商定制计费规则（进阶定价方案家族中的特例，旗舰 = MiniMax）；仅 VIDEO 模式可用。
     *
     * <p>专属口径由 {@code VideoBillingModeCalculator#calculateVendorMiniMax} 实现：
     * 输出秒 × 档位输出价 + 输入秒 × 档位输入价（默认回落同档位输出价）
     * + max(0, 图片张数 − 免费张数) × 每张固定价；音频输入免费、不折输入秒、无阶梯/错峰。</p>
     *
     * <p>方案层 {@code VendorMiniMaxPricing} 的取价语义与 {@link #FIXED} 一致——它只负责
     * 「按档位键取单价」，多分量的编排在计算器，方案值不作为独立的价格算法。</p>
     */
    VENDOR_MINIMAX("厂商定制(MiniMax)", BillingModeEnum.VIDEO);

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
