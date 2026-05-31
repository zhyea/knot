package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.error.ErrorCode;

public class GatewayAuthException extends GatewayRequestException {

    /**
     * Constructs an authentication error.
     */
    public GatewayAuthException(String message) {
        super(message, GatewayErrorTypeEnum.AUTH_ERROR.code(), ErrorCode.UNAUTHORIZED.code());
    }
}
