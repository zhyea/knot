package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;

public class GatewayUpstreamException extends GatewayRequestException {

    /**
     * Constructs an upstream gateway error.
     */
    public GatewayUpstreamException(String message, String code) {
        super(message, GatewayErrorTypeEnum.UPSTREAM_ERROR.code(), code);
    }
}
