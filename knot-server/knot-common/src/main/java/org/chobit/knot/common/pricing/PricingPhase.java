package org.chobit.knot.common.pricing;

import org.chobit.knot.common.constants.enums.EnumOption;

/**
 * 高低峰相位。
 *
 * <p>PEAK = 高峰（通常按基础价计费），OFF_PEAK = 低峰（按 multiplier 打折）。
 * label 随枚举下发前端（/api/enum-options），不再前端自建第二份相位名。</p>
 */
public enum PricingPhase implements EnumOption {

    PEAK("PEAK", "高峰"),
    OFF_PEAK("OFF_PEAK", "低峰");

    private final String code;
    private final String label;

    PricingPhase(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
   public String label() {
        return label;
    }

    /** 按配置里的相位名取值；未知/空返回 null 由校验层报错 */
    public static PricingPhase fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (PricingPhase phase : values()) {
            if (phase.code.equals(normalized)) {
                return phase;
            }
        }
        return null;
    }
}
