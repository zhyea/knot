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
     * Creates an upstream error response carrying the <b>original upstream status</b>.
     *
     * <p>网关对上游非 2xx 统一以 502 回给客户端（错误码隔离，避免上游语义穿透），但若同时丢弃
     * 上游原状态码，调用方就无法区分「余额不足 402」「限流 429」与「真故障 5xx」。此处仅在响应体
     * 里透出原状态码与摘要（HTTP 状态码仍为 502），供有诊断需求的调用方（如管理端计费裁判）
     * 做语义还原——对既有调用方是纯新增字段，向后兼容。</p>
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
