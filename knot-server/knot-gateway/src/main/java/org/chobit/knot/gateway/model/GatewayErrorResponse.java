package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        return new GatewayErrorResponse(new ErrorBody(message, type, code, null));
    }

    /**
     * Creates a rate limit / quota rejection response carrying the structured detail.
     */
    public static GatewayErrorResponse ofRejection(String message, String type, String code, TrafficRejectDetail detail) {
        return new GatewayErrorResponse(new ErrorBody(message, type, code, detail));
    }

    public record ErrorBody(String message,
                            String type,
                            String code,
                            @JsonInclude(JsonInclude.Include.NON_NULL) TrafficRejectDetail detail) {
    }
}
