package org.chobit.knot.gateway.pricing;

/**
 * 高低峰相位。
 *
 * <p>PEAK = 高峰（通常按基础价计费），OFF_PEAK = 低峰（按 multiplier 打折）。
 */
public enum PricingPhase {

    PEAK("PEAK"),
    OFF_PEAK("OFF_PEAK");

    private final String code;

    PricingPhase(String code) {
        this.code = code;
    }

    public String code() {
        return code;
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
