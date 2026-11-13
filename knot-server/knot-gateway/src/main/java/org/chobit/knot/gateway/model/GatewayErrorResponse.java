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
        return new GatewayErrorResponse(new ErrorBody(message, type, code, null, null, null));
    }

    /**
     * Creates a rate limit / quota rejection response carrying the structured detail.
     */
    public static GatewayErrorResponse ofRejection(String message, String type, String code, TrafficRejectDetail detail) {
        return new GatewayErrorResponse(new ErrorBody(message, type, code, detail, null, null));
    }

    /**
     * Creates an upstream error response carrying the original upstream status.
     * 网关对上游非 2xx 统一以 502 回给客户端，仅在响应体里透出原状态码与摘要（HTTP 状态码仍为 502）。
     */
    public static GatewayErrorResponse ofUpstream(String message, String type, String code,
                                                  Integer upstreamStatus, String upstreamMessage) {
        return new GatewayErrorResponse(new ErrorBody(message, type, code, null, upstreamStatus, upstreamMessage));
    }

    public record ErrorBody(String message,
                            String type,
                            String code,
                            @JsonInclude(JsonInclude.Include.NON_NULL) TrafficRejectDetail detail,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Integer upstreamStatus,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String upstreamMessage) {
    }
}
