package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;

/**
 * 流量被拒绝的原因：决定网关返回给调用方的错误码。
 */
public enum TrafficRejectReason {

    /**
     * 超出频控阈值（秒级或分钟级窗口）。
     */
    RATE_LIMIT(ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED),

    /**
     * 超出日请求数额度。
     */
    QUOTA_DAILY(ProxyErrorCodeEnum.QUOTA_EXCEEDED),

    /**
     * 超出月请求数额度。
     */
    QUOTA_MONTHLY(ProxyErrorCodeEnum.QUOTA_EXCEEDED),

    /**
     * 超出累计 token 额度。
     */
    QUOTA_TOKEN(ProxyErrorCodeEnum.QUOTA_EXCEEDED);

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
     * 是否为额度类拒绝（区别于频控类）。
     */
    public boolean quota() {
        return this != RATE_LIMIT;
    }
}
