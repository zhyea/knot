package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;

public class GatewayUpstreamException extends GatewayRequestException {

    private final Integer httpStatus;
    private final String responseBody;

    /**
     * Constructs an upstream gateway error.
     */
    public GatewayUpstreamException(String message, String code) {
        this(message, code, null, null);
    }

    /**
     * Constructs an upstream gateway error with upstream response details.
     */
    public GatewayUpstreamException(String message, String code, Integer httpStatus, String responseBody) {
        super(message, GatewayErrorTypeEnum.UPSTREAM_ERROR.code(), code);
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public String responseBody() {
        return responseBody;
    }
}
