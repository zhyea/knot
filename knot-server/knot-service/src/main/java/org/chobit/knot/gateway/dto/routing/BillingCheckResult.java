package org.chobit.knot.gateway.dto.routing;

/**
 * 单条自检结果：校验项 + 三态 + 说明。
 *
 * <p>纯数据载体，由 {@link BillingFactChecks} 产出；{@code message} 在 FAIL/SKIP 时
 * 给出可读原因，PASS 时为空，供抽屉自检区直接展示。</p>
 *
 * @param code    自检项
 * @param status  三态
 * @param message 失败/跳过原因（PASS 为空）
 */
public record BillingCheckResult(BillingCheckCode code, BillingCheckStatus status, String message) {

    public static BillingCheckResult pass(BillingCheckCode code) {
        return new BillingCheckResult(code, BillingCheckStatus.PASS, null);
    }

    public static BillingCheckResult fail(BillingCheckCode code, String message) {
        return new BillingCheckResult(code, BillingCheckStatus.FAIL, message);
    }

    public static BillingCheckResult skip(BillingCheckCode code, String message) {
        return new BillingCheckResult(code, BillingCheckStatus.SKIP, message);
    }

    /** 是否通过（PASS 为真，FAIL/SKIP 为假） */
    public boolean isPass() {
        return status == BillingCheckStatus.PASS;
    }
}
