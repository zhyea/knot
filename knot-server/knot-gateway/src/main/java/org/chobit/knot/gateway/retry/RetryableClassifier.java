package org.chobit.knot.gateway.retry;

import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.RetryPolicy;

/**
 * 判断一次上游失败是否值得重试。
 *
 * <p>口径（与 docs/design/routing-retry-design.md §5 一致）：</p>
 * <ul>
 *   <li>上游有 HTTP 状态时按 {@code retryOn} 集合判定（默认 allowlist：5xx 与 429）；</li>
 *   <li>没有 HTTP 状态时按错误码兜底——网络层异常（连接/读超时、连接重置）可重试，
 *       配置类错误（模型/供应商缺失、协议未配置）与自身频控/额度拒绝不可重试。</li>
 * </ul>
 *
 * <p>只认 {@link GatewayUpstreamException}：重试判定只针对上游调用失败，
 * 网关自身的异常（鉴权、频控、额度、代码缺陷）一律不重试，直接上抛。</p>
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
        if (error == null) {
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
     * 无 HTTP 状态时的兜底：只有网络/传输层失败才值得重试。
     *
     * <p>{@code UPSTREAM_ERROR} 在无状态时来自执行器的通用 catch（连接超时、读超时、
     * 连接重置、EOF 等），重试有效；其余错误码多为配置问题，重试多少次结果都一样。</p>
     */
    private static boolean retryableByCode(String code) {
        if (code == null) {
            return false;
        }
        return switch (code) {
            case "UPSTREAM_ERROR" -> true;
            case "MODEL_NOT_FOUND", "PROVIDER_NOT_FOUND", "API_PROTOCOL_NOT_CONFIGURED",
                 "MISSING_MODEL", "NO_ROUTING_TARGET", "RATE_LIMIT_EXCEEDED", "QUOTA_EXCEEDED" -> false;
            default -> false;
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
                || ProxyErrorCodeEnum.API_PROTOCOL_NOT_CONFIGURED.code().equals(code);
    }
}
