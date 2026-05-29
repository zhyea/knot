package org.chobit.knot.gateway.constants;

public final class ProxyErrorCodes {

    public static final String MISSING_MODEL = "MISSING_MODEL";
    public static final String MODEL_NOT_FOUND = "MODEL_NOT_FOUND";
    public static final String PROVIDER_NOT_FOUND = "PROVIDER_NOT_FOUND";
    public static final String API_PROTOCOL_NOT_CONFIGURED = "API_PROTOCOL_NOT_CONFIGURED";
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String NO_ROUTING_TARGET = "NO_ROUTING_TARGET";
    public static final String UPSTREAM_ERROR = "UPSTREAM_ERROR";

    private ProxyErrorCodes() {
    }
}
