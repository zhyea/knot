package org.chobit.knot.common.constants.enums;

public enum ProxyErrorCodeEnum {
    MISSING_MODEL("MISSING_MODEL"),
    MODEL_NOT_FOUND("MODEL_NOT_FOUND"),
    PROVIDER_NOT_FOUND("PROVIDER_NOT_FOUND"),
    API_PROTOCOL_NOT_CONFIGURED("API_PROTOCOL_NOT_CONFIGURED"),
    UPSTREAM_ENDPOINT_NOT_CONFIGURED("UPSTREAM_ENDPOINT_NOT_CONFIGURED"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED"),
    QUOTA_EXCEEDED("QUOTA_EXCEEDED"),
    NO_ROUTING_TARGET("NO_ROUTING_TARGET"),
    UPSTREAM_ERROR("UPSTREAM_ERROR"),
    /**
     * 上游 HTTP 成功（2xx）但响应体为空：null / 空串 / 纯空白。
     *
     * <p>与 {@link #UPSTREAM_ERROR} 分开：那个是「上游报了错」，本项是「上游没报错，但什么都没给」。
     * 处置也不同——空响应是<b>确定性失败</b>，同目标重投与换候选重投都只会再空一次，
     * 直接终止本次请求（判据见 {@code RetryableClassifier#terminal}）。</p>
     */
    UPSTREAM_EMPTY_RESPONSE("UPSTREAM_EMPTY_RESPONSE");

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
