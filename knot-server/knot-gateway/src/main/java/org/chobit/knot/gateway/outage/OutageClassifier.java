package org.chobit.knot.gateway.outage;

import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.common.model.EjectionPolicy;

/**
 * 判断一次上游失败是否算「这个模型本身的异常」，即是否计入摘除判定。
 * 与 {@link org.chobit.knot.gateway.retry.RetryableClassifier} 的分工：重试关心「再试一次有没有用」，摘除关心「这个目标是不是坏了」。
 * 计入：5xx、网络超时 / 连接错误、上游 429；不计入：4xx 业务错误与配置类错误（模型 / 供应商缺失、协议未配置）。
 */
public final class OutageClassifier {

    private OutageClassifier() {
    }

    /**
     * 该次上游失败是否计入摘除判定。
     *
     * @param error  上游异常，null 视为不计
     * @param policy 规则级摘除策略，null 或关闭时一律不计
     */
    public static boolean countable(GatewayUpstreamException error, EjectionPolicy policy) {
        if (error == null || policy == null || !policy.isEnabled() || !policy.isCountFailures()) {
            return false;
        }
        // 空响应没有可用的上游状态码（2xx 是「成功」信号，不能拿来分流），
        // 但它恰是「这个目标确实坏了」的直接证据：连续多次空响应必须能摘除，
        // 否则坏目标会永久承接流量，每次请求都以一次 502 收场。故按错误码优先判定。
        if (ProxyErrorCodeEnum.UPSTREAM_EMPTY_RESPONSE.code().equals(error.code())) {
            return true;
        }
        Integer status = error.httpStatus();
        if (status != null) {
            return countableStatus(status);
        }
        return countableByCode(error.code());
    }

    /**
     * 有 HTTP 状态时：5xx 与上游 429 计（上游自身故障或限流）；其余状态码一律不计。
     */
    private static boolean countableStatus(int status) {
        return status >= 500 || status == 429;
    }

    /**
     * 无 HTTP 状态时按错误码兜底：只有网络/传输层失败才说明目标可能坏了。
     * 配置类错误（模型缺失、供应商缺失、协议未配置）与网关自身频控/额度拒绝都不计。
     * 注意码表归属：入参是 {@link GatewayUpstreamException#code()}，走
     * {@link ProxyErrorCodeEnum} 族（构造点见 {@code AbstractUpstreamProtocolExecutor}）；
     * 与 {@link org.chobit.knot.common.constants.enums.GatewayErrorTypeEnum#UPSTREAM_ERROR}
     * 只是字符串同名，属另一个码表，不可互换。
     */
    private static boolean countableByCode(String code) {
        if (code == null) {
            return false;
        }
        return ProxyErrorCodeEnum.UPSTREAM_ERROR.code().equals(code);
    }
}
