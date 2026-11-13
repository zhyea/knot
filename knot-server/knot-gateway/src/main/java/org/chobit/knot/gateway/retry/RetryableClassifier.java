package org.chobit.knot.gateway.retry;

import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.common.model.RetryPolicy;

/**
 * 判断一次上游失败是否值得重试。
 * 上游有 HTTP 状态时按 {@code retryOn} 集合判定（默认 allowlist：5xx 与 429）；没有 HTTP 状态时按错误码兜底：
 * 网络层异常（连接/读超时、连接重置）可重试，配置类错误与自身频控/额度拒绝不可重试。
 * 只认 {@link GatewayUpstreamException}：网关自身的异常一律不重试，直接上抛。
 */
public final class RetryableClassifier {

    private RetryableClassifier() {
    }

    /**
     * 返回该次上游失败是否应当重试。
     *
     * @param error  上游异常，null 视为不可重试
     * @param policy 规则级重试策略，null 视为默认策略
     */
    public static boolean retryable(GatewayUpstreamException error, RetryPolicy policy) {
        if (error == null || terminal(error)) {
            return false;
        }
        RetryPolicy effective = policy == null ? RetryPolicy.DEFAULT : policy;
        if (!effective.retryable()) {
            return false;
        }
        Integer status = error.httpStatus();
        if (status != null) {
            return effective.matchesStatus(status);
        }
        return retryableByCode(error.code());
    }

    /**
     * 是否属于「确定性上游失败」：重试与 failover 都不会改变结果，必须直接终止本次请求。
     * 目前只有空响应一条（{@link ProxyErrorCodeEnum#UPSTREAM_EMPTY_RESPONSE}），即上游 HTTP 成功但没有返回内容。
     * 两处消费：{@code retryable()} 关掉同目标重试，{@code GatewayRequestHandler} 跳过跨候选 failover，两处必须同源。
     */
    public static boolean terminal(GatewayUpstreamException error) {
        return error != null
                && ProxyErrorCodeEnum.UPSTREAM_EMPTY_RESPONSE.code().equals(error.code());
    }

    /**
     * 无 HTTP 状态时的兜底：只有网络/传输层失败才值得重试。
     * {@code UPSTREAM_ERROR} 在无状态时来自执行器的通用 catch（连接超时、读超时、连接重置、EOF 等），其余错误码不可重试。
     * 判定穷举 {@link ProxyErrorCodeEnum} 而不手写 code 字符串：新增常量时此处编译失败，强制表态。
     */
    private static boolean retryableByCode(String code) {
        ProxyErrorCodeEnum type = ProxyErrorCodeEnum.fromCode(code);
        if (type == null) {
            // 未知码理论上不该出现；宁可少重试一次，也不重试一个看不懂的失败
            return false;
        }
        return switch (type) {
            case UPSTREAM_ERROR -> true;
            case MISSING_MODEL, MODEL_NOT_FOUND, PROVIDER_NOT_FOUND, API_PROTOCOL_NOT_CONFIGURED,
                 UPSTREAM_ENDPOINT_NOT_CONFIGURED, RATE_LIMIT_EXCEEDED, QUOTA_EXCEEDED,
                 NO_ROUTING_TARGET -> false;
            // 空响应是确定性失败，正常路径上已被 terminal() 短路（见 retryable 第一行）；
            // 此处显式列出只为让穷举完整，语义是「即使漏网也不重试」
            case UPSTREAM_EMPTY_RESPONSE -> false;
        };
    }

    /**
     * 供调用方复用：错误码是否属于「配置类」——这类失败重试无意义。
     */
    public static boolean configurationError(GatewayUpstreamException error) {
        if (error == null) {
            return false;
        }
        String code = error.code();
        return ProxyErrorCodeEnum.MODEL_NOT_FOUND.code().equals(code)
                || ProxyErrorCodeEnum.PROVIDER_NOT_FOUND.code().equals(code)
                || ProxyErrorCodeEnum.API_PROTOCOL_NOT_CONFIGURED.code().equals(code)
                || ProxyErrorCodeEnum.UPSTREAM_ENDPOINT_NOT_CONFIGURED.code().equals(code);
    }
}
