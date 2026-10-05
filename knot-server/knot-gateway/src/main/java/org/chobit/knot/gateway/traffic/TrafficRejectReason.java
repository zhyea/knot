package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;

/**
 * 流量被拒绝的原因：决定网关返回给调用方的错误码。
 */
public enum TrafficRejectReason {

    /**
     * 超出每分钟请求数上限（RPM）。
     */
    RATE_LIMIT_RPM(ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED),

    /**
     * 超出每分钟 token 上限（TPM）。
     */
    RATE_LIMIT_TPM(ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED),

    /**
     * 超出累计 token 限额。
     */
    QUOTA_TOKENS(ProxyErrorCodeEnum.QUOTA_EXCEEDED),

    /**
     * 超出累计成本上限。
     */
    QUOTA_COST(ProxyErrorCodeEnum.QUOTA_EXCEEDED);

    private final ProxyErrorCodeEnum errorCode;

    TrafficRejectReason(ProxyErrorCodeEnum errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * 对应的网关错误码。
     */
    public ProxyErrorCodeEnum errorCode() {
        return errorCode;
    }

    /**
     * 是否为限额类拒绝（区别于限流类）。
     */
    public boolean quota() {
        return this == QUOTA_TOKENS || this == QUOTA_COST;
    }
}
