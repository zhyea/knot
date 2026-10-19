package org.chobit.knot.gateway.constants.enums;

public enum GatewayErrorTypeEnum {
    AUTH_ERROR("auth_error"),
    INVALID_REQUEST_ERROR("invalid_request_error"),
    RATE_LIMIT_ERROR("rate_limit_error"),
    QUOTA_ERROR("quota_error"),
    MODERATION_ERROR("moderation_error"),
    /**
     * 网关自身依赖不可用（当前只有共享状态存储 Redis）。与 {@link #UPSTREAM_ERROR} 分开：
     * 上游不可用是「模型服务的问题」，本项是「网关自己的存储挂了」，
     * 两者处置完全不同（一个可重试/换节点，一个必须先修 Redis），日志告警也需分别配规则。
     */
    STORAGE_UNAVAILABLE("storage_unavailable"),
    UPSTREAM_ERROR("upstream_error"),
    /**
     * 网关请求预算超时（B2 全局 deadline）：请求在 {@code request-budget} 内未就绪即终止返回 504。
     * 与 {@link #UPSTREAM_ERROR} 分开——前者是「网关自己设定的总时限到了」，后者是「上游真的回了错误或挂死」，
     * 两者排查口径不同，错误码也要能区分。
     */
    TIMEOUT_ERROR("timeout_error");

    private final String code;

    GatewayErrorTypeEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}
