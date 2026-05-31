package org.chobit.knot.gateway.constants.enums;

public enum GatewayErrorTypeEnum {
    AUTH_ERROR("auth_error"),
    INVALID_REQUEST_ERROR("invalid_request_error"),
    RATE_LIMIT_ERROR("rate_limit_error"),
    UPSTREAM_ERROR("upstream_error");

    private final String code;

    GatewayErrorTypeEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}
