package org.chobit.knot.gateway.outage;

import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.EjectionPolicy;

/**
 * 判断一次上游失败是否算「这个模型本身的异常」，即是否计入摘除判定。
 *
 * <p>与 {@link org.chobit.knot.gateway.retry.RetryableClassifier} 的区别：重试关心「再试一次有没有用」，
 * 摘除关心「这个目标是不是坏了」。两者的交集很大（5xx、网络超时），但有明确分歧：</p>
 * <ul>
 *   <li><b>上游 429</b>：重试有意义（等 Retry-After），摘除更有意义（上游已在限流，应尽快把流量切走）；</li>
 *   <li><b>4xx 业务错误</b>：可能重试（默认 retryOn 含 4xx），但绝不摘除——参数错、鉴权错是调用方的锅；</li>
 *   <li><b>配置类错误</b>（模型/供应商缺失、协议未配置）：重试与摘除都不该发生，重试多少次结果都一样，
 *       摘了反而把「配置没配好」伪装成「上游挂了」。</li>
 * </ul>
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
     *
     * <p>配置类错误（模型缺失、供应商缺失、协议未配置）与网关自身频控/额度拒绝都不计。</p>
     */
    private static boolean countableByCode(String code) {
        if (code == null) {
            return false;
        }
        return "UPSTREAM_ERROR".equals(code);
    }
}
