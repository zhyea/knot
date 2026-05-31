package org.chobit.knot.gateway.constants.enums;

public enum ProxyErrorCodeEnum {
    MISSING_MODEL("MISSING_MODEL"),
    MODEL_NOT_FOUND("MODEL_NOT_FOUND"),
    PROVIDER_NOT_FOUND("PROVIDER_NOT_FOUND"),
    API_PROTOCOL_NOT_CONFIGURED("API_PROTOCOL_NOT_CONFIGURED"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED"),
    NO_ROUTING_TARGET("NO_ROUTING_TARGET"),
    UPSTREAM_ERROR("UPSTREAM_ERROR");

    private final String code;

    ProxyErrorCodeEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}
