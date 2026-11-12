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

    /** 上游失败的内部错误码（全大写，与常量同名）。 */
    public String code() {
        return code;
    }

    /**
     * 按 code 反查枚举；{@code null} 与未知值都返回 {@code null}（大小写敏感，code 全大写）。
     *
     * <p>存在的意义是让调用方写 {@code switch (枚举)} 穷举，而不是手写字符串字面量：
     * 新增一个 code 时，漏掉分支的地方会编译失败，而不是静默落进 {@code default}。</p>
     */
    public static ProxyErrorCodeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProxyErrorCodeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
