package org.chobit.knot.gateway.constants;

public final class GatewayErrorTypes {

    public static final String AUTH_ERROR = "auth_error";
    public static final String INVALID_REQUEST_ERROR = "invalid_request_error";
    public static final String RATE_LIMIT_ERROR = "rate_limit_error";
    public static final String UPSTREAM_ERROR = "upstream_error";

    private GatewayErrorTypes() {
    }
}
