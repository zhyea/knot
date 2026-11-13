package org.chobit.knot.common.constants.enums;

public enum GatewayErrorTypeEnum {
    AUTH_ERROR("auth_error"),
    INVALID_REQUEST_ERROR("invalid_request_error"),
    RATE_LIMIT_ERROR("rate_limit_error"),
    QUOTA_ERROR("quota_error"),
    MODERATION_ERROR("moderation_error"),
    /**
     * 网关自身依赖不可用（当前只有共享状态存储 Redis），与 {@link #UPSTREAM_ERROR}（上游模型服务不可用）区分。
     */
    STORAGE_UNAVAILABLE("storage_unavailable"),
    UPSTREAM_ERROR("upstream_error"),
    /**
     * 网关请求预算超时：请求在 {@code request-budget} 内未就绪即终止，返回 504。
     * 与 {@link #UPSTREAM_ERROR} 分开：本项是网关自己设定的总时限到了，后者是上游回了错误或挂死。
     */
    TIMEOUT_ERROR("timeout_error"),
    /**
     * 网关自身的未预期异常（兜底 500）。数仓侧 {@code status} 落 {@code gateway_error}
     * （{@code GatewayTrafficMsgDto#statusOf} 显式声明），与 {@link #UPSTREAM_ERROR} 分开统计。
     */
    INTERNAL_ERROR("internal_error");

    private final String code;

    GatewayErrorTypeEnum(String code) {
        this.code = code;
    }

    /**
     * 错误类型码：HTTP 响应体（{@code GatewayErrorResponse}）与数仓消息（{@code ErrorMsgDto}）
     * 共用的单一来源，两侧取值必须一致，不得各写字面量。
     */
    public String code() {
        return code;
    }

    /**
     * 按 wire code 反查枚举；未知值与 {@code null} 一律返回 {@code null}，由调用方决定兜底语义。
     */
    public static GatewayErrorTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (GatewayErrorTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
