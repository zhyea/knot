package org.chobit.knot.svc.dto.routing;

/**
 * 自检项三态：通过 / 失败 / 跳过。
 *
 * 判不了的一律 {@link #SKIP} 并说明原因，不充 {@link #PASS}；自检结论只进抽屉自检区，不进 JEV {@code state}。
 */
public enum BillingCheckStatus {

    /** 校验通过 */
    PASS,

    /** 校验失败（明确含义见 {@code BillingCheckResult.message}） */
    FAIL,

    /** 无法判定（缺依据），绝不充 PASS */
    SKIP
}
