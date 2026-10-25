package org.chobit.knot.gateway.exception;

import org.chobit.knot.common.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.common.error.ErrorCode;

public class GatewayInvalidRequestException extends GatewayRequestException {

    /**
     * Constructs an invalid request error.
     */
    public GatewayInvalidRequestException(String message) {
        super(message, GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code(), ErrorCode.VALIDATION_ERROR.code());
    }
}
