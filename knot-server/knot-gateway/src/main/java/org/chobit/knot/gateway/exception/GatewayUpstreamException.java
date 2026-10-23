package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;

public class GatewayUpstreamException extends GatewayRequestException {

    private final Integer httpStatus;
    private final String responseBody;
    private final Long retryAfterMs;
    /**
     * 上游供应商系统里这次请求的 id（响应头白名单归一，见 {@code UpstreamRequestIdResolver}）：
     * 与供应商联合排查时对方唯一认的检索键。拿不到为 null（不是空串）。
     */
    private final String upstreamRequestId;
    /**
     * 本次上游调用的完整请求地址（scheme + host + path）：落 Kafka 流量事件与上游日志，
     * 排障能直接看出请求打到了哪个供应商端点。配置缺失 / 请求未发起时为 null。
     */
    private final String upstreamUrl;

    /**
     * Constructs an upstream gateway error.
     */
    public GatewayUpstreamException(String message, String code) {
        this(message, code, null, null, null, null, null);
    }

    /**
     * Constructs an upstream gateway error with upstream response details.
     */
    public GatewayUpstreamException(String message, String code, Integer httpStatus, String responseBody) {
        this(message, code, httpStatus, responseBody, null, null, null);
    }

    /**
     * Constructs an upstream gateway error with upstream response details and retry hint.
     *
     * @param retryAfterMs 上游 {@code Retry-After} 换算的毫秒数；无该头或解析失败传 null
     */
    public GatewayUpstreamException(String message, String code, Integer httpStatus, String responseBody,
                                    Long retryAfterMs) {
        this(message, code, httpStatus, responseBody, retryAfterMs, null, null);
    }

    /**
     * Constructs an upstream gateway error with upstream response details, retry hint
     * and the supplier-side request id.
     *
     * @param retryAfterMs     上游 {@code Retry-After} 换算的毫秒数；无该头或解析失败传 null
     * @param upstreamRequestId 上游响应头归一的 request-id；无命中传 null
     */
    public GatewayUpstreamException(String message, String code, Integer httpStatus, String responseBody,
                                    Long retryAfterMs, String upstreamRequestId) {
        this(message, code, httpStatus, responseBody, retryAfterMs, upstreamRequestId, null);
    }

    /**
     * 完整构造：携带上游响应状态、错误体、重试提示、供应商侧请求 id 与本次调用的完整请求地址。
     *
     * @param retryAfterMs     上游 {@code Retry-After} 换算的毫秒数；无该头或解析失败传 null
     * @param upstreamRequestId 上游响应头归一的 request-id；无命中传 null
     * @param upstreamUrl       本次上游调用的完整请求地址（scheme + host + path）；未发起调用传 null
     */
    public GatewayUpstreamException(String message, String code, Integer httpStatus, String responseBody,
                                    Long retryAfterMs, String upstreamRequestId, String upstreamUrl) {
        super(message, GatewayErrorTypeEnum.UPSTREAM_ERROR.code(), code);
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.retryAfterMs = retryAfterMs;
        this.upstreamRequestId = upstreamRequestId;
        this.upstreamUrl = upstreamUrl;
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

    public String upstreamRequestId() {
        return upstreamRequestId;
    }

    public String upstreamUrl() {
        return upstreamUrl;
    }
}
