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
    VENDOR_MINIMAX("厂商定制(MiniMax)", BillingModeEnum.VIDEO),

    /**
     * 维度分档价：按「请求属性」分档取价（分辨率 × 是否输出声音 × 有无输入/参考视频）；仅 VIDEO 模式可用。
     *
     * <p>与 {@link #TIERED}（按<b>用量</b>分档）、{@link #PEAK_OFF_PEAK}（按<b>时间</b>分档）
     * 构成三根正交的轴：本方案决定单价取自哪个属性组合，另两根分别决定「量落在哪档」与「时点打几折」。
     * 三者可叠加——分档价一律过方案层倍率链（{@code resolveDimensionPrice}），正是为此。</p>
     *
     * <p><b>键空间</b>（每个档位 4 个组合键，按顺序逐级去尾降级）：</p>
     * <pre>
     * 档位:REFERENCE:AUDIO   有声 + 有输入视频
     * 档位:REFERENCE         无声 + 有输入视频
     * 档位:AUDIO             有声 + 无输入视频
     * 档位                   无声 + 无输入视频
     * </pre>
     *
     * <p>⚠ 「有无输入视频」≡「有无参考视频」（robin 2026-10-26 定调）：视频生成里传进去的那个视频
     * 既是计费时长来源也是内容参考，二者不需要拆成两维；参考<b>图</b>不属于这一维，
     * 走图片张数口径（MiniMax 那种「按张计费」是另一回事，见 {@link #VENDOR_MINIMAX}）。</p>
     *
     * <p>取价语义与 {@link #FIXED} 一致（无阶梯、无相位倍率），本方案真正的增量是<b>保存期的强校验</b>：
     * 维度键拼错（{@code 720P:SOUND}、{@code 720P:AUDIO:REFERENCE}、空档位名）在 FIXED 下会
     * <b>静默回落默认价</b>——配置看着生效、实际从未命中，本方案直接拒绝保存。</p>
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
