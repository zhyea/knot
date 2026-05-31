package org.chobit.knot.gateway.exception;

public abstract class GatewayRequestException extends RuntimeException {

    private final String type;
    private final String code;

    /**
     * Constructs a gateway request exception.
     */
    protected GatewayRequestException(String message, String type, String code) {
        super(message);
        this.type = type;
        this.code = code;
    }

    /**
     * Returns the gateway error type.
     */
    public String type() {
        return type;
    }

    /**
     * Returns the optional business error code.
     */
    public String code() {
        return code;
    }
}
