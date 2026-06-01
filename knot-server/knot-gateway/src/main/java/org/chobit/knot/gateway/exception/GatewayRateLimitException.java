package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;

public class GatewayRateLimitException extends GatewayRequestException {

    /**
     * Constructs a rate limit error.
     */
    public GatewayRateLimitException(String message, String code) {
        super(message, GatewayErrorTypeEnum.RATE_LIMIT_ERROR.code(), code);
    }
}
