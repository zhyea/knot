package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;

/**
 * 流量被拒绝的原因：决定网关返回给调用方的错误码，以及响应体里怎么描述这次拒绝。
 */
public enum TrafficRejectReason {

    /**
     * 超出每分钟请求数上限（RPM）。
     */
    RATE_LIMIT_RPM(ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED, "RPM", "requests/minute", false),

    /**
     * 超出每分钟 token 上限（TPM）。
     */
    RATE_LIMIT_TPM(ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED, "TPM", "tokens/minute", false),

    /**
     * 超出累计 token 限额。
     */
    QUOTA_TOKENS(ProxyErrorCodeEnum.QUOTA_EXCEEDED, "TOKENS", "tokens", true),

    /**
     * 超出累计成本上限；单位取策略币种，见 {@link TrafficDecision#currency()}。
     */
    QUOTA_COST(ProxyErrorCodeEnum.QUOTA_EXCEEDED, "COST", null, true);

    private final ProxyErrorCodeEnum errorCode;
    private final String dimension;
    private final String unit;
    private final boolean quota;

    TrafficRejectReason(ProxyErrorCodeEnum errorCode, String dimension, String unit, boolean quota) {
        this.errorCode = errorCode;
        this.dimension = dimension;
        this.unit = unit;
        this.quota = quota;
    }

    /**
     * 对应的网关错误码。
     */
    public ProxyErrorCodeEnum errorCode() {
        return errorCode;
    }

    /**
     * 被限的维度：{@code RPM} / {@code TPM} / {@code TOKENS} / {@code COST}。
     */
    public String dimension() {
        return dimension;
    }

    /**
     * 计量单位；成本维度返回 {@code null}——单位取决于策略币种。
     */
    public String unit() {
        return unit;
    }

    /**
     * 是否为限额类拒绝（区别于限流类）。
     */
    public boolean quota() {
        return quota;
    }
}
