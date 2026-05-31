package org.chobit.knot.gateway.model;

public record GatewayErrorResponse(ErrorBody error) {

    /**
     * Creates an error response without a business code.
     */
    public static GatewayErrorResponse of(String message, String type) {
        return of(message, type, null);
    }

    /**
     * Creates an error response with an optional business code.
     */
    public static GatewayErrorResponse of(String message, String type, String code) {
        return new GatewayErrorResponse(new ErrorBody(message, type, code));
    }

    public record ErrorBody(String message, String type, String code) {
    }
}
