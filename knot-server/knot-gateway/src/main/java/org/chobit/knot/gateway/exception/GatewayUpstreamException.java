package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;

public class GatewayUpstreamException extends GatewayRequestException {

    private final Integer httpStatus;
    private final String responseBody;
    private final Long retryAfterMs;

    /**
     * Constructs an upstream gateway error.
     */
    public GatewayUpstreamException(String message, String code) {
        this(message, code, null, null, null);
    }

    /**
     * Constructs an upstream gateway error with upstream response details.
     */
    public GatewayUpstreamException(String message, String code, Integer httpStatus, String responseBody) {
        this(message, code, httpStatus, responseBody, null);
    }

    /**
     * Constructs an upstream gateway error with upstream response details and retry hint.
     *
     * @param retryAfterMs 上游 {@code Retry-After} 换算的毫秒数；无该头或解析失败传 null
     */
    public GatewayUpstreamException(String message, String code, Integer httpStatus, String responseBody,
                                    Long retryAfterMs) {
        super(message, GatewayErrorTypeEnum.UPSTREAM_ERROR.code(), code);
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.retryAfterMs = retryAfterMs;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public String responseBody() {
        return responseBody;
    }

    /**
     * 上游建议的重试等待时长（毫秒）；未给出或无法解析时为 null。
     *
     * <p>只有 429/503 这类明确限流响应才会携带；重试退避在策略开启
     * {@code respectRetryAfter} 时取该值与指数退避的较大者。</p>
     */
    public Long retryAfterMs() {
        return retryAfterMs;
    }
}
