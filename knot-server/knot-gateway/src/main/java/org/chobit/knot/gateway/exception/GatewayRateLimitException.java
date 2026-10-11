package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.model.TrafficRejectDetail;

public class GatewayRateLimitException extends GatewayTrafficRejectException {

    /**
     * Constructs a rate limit error.
     *
     * @param message 面向调用方的说明
     * @param code    业务错误码
     * @param detail  被拒明细
     */
    public GatewayRateLimitException(String message, String code, TrafficRejectDetail detail) {
        super(message, GatewayErrorTypeEnum.RATE_LIMIT_ERROR.code(), code, detail);
    }
}
