package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.model.TrafficRejectDetail;

/**
 * 额度耗尽：窗口内累计 token 或成本达到策略上限。
 *
 * <p>与限流一样按 429 返回——调用方只需一套重试处置；用 {@code type=quota_error}、
 * {@code code=QUOTA_EXCEEDED} 与限流区分开。</p>
 */
public class GatewayQuotaException extends GatewayTrafficRejectException {

    /**
     * Constructs a quota exceeded error.
     *
     * @param message 面向调用方的说明
     * @param code    业务错误码
     * @param detail  被拒明细
     */
    public GatewayQuotaException(String message, String code, TrafficRejectDetail detail) {
        super(message, GatewayErrorTypeEnum.QUOTA_ERROR.code(), code, detail);
    }
}
