package org.chobit.knot.gateway.pricing;

/**
 * 高低峰判定原因：给预览与排障用，决定角色的是 {@link PricingPhase}，本枚举只解释“为什么”。
 */
public enum PhaseReason {

    /** 命中某条高峰规则的星期 + 时段窗口 */
    PEAK_WINDOW("PEAK_WINDOW"),

    /** 日历判定为节假日，整日低峰（覆盖星期与时段） */
    HOLIDAY("HOLIDAY"),

    /** 日历判定为调休补班日，且策略要求整日低峰 */
    MAKE_UP_WORKDAY("MAKE_UP_WORKDAY"),

    /** 无任何规则命中，落到兜底低峰 */
    DEFAULT("DEFAULT"),

    /** 计费上下文没有 occurredAt，无法判定峰谷，不放大也不打折 */
    NO_TIMESTAMP("NO_TIMESTAMP");

    private final String code;

    PhaseReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
