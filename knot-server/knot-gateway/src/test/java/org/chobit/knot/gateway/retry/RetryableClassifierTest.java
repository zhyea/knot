package org.chobit.knot.gateway.retry;

import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：哪些上游失败值得重试。
 *
 * <p>核心口径：5xx / 429 与网络层异常可重试；4xx 与配置类错误不重试（重试多少次结果都一样）。</p>
 */
class RetryableClassifierTest {

    private static final RetryPolicy DEFAULT = RetryPolicy.DEFAULT;

    @Test
    void serverErrorsAndRateLimitAreRetryable() {
        assertTrue(RetryableClassifier.retryable(error(500), DEFAULT));
        assertTrue(RetryableClassifier.retryable(error(502), DEFAULT));
        assertTrue(RetryableClassifier.retryable(error(503), DEFAULT));
        assertTrue(RetryableClassifier.retryable(error(504), DEFAULT));
        assertTrue(RetryableClassifier.retryable(error(429), DEFAULT));
    }

    @Test
    void clientErrorsAreNotRetryable() {
        assertFalse(RetryableClassifier.retryable(error(400), DEFAULT));
        assertFalse(RetryableClassifier.retryable(error(401), DEFAULT));
        assertFalse(RetryableClassifier.retryable(error(403), DEFAULT));
        assertFalse(RetryableClassifier.retryable(error(404), DEFAULT));
        assertFalse(RetryableClassifier.retryable(error(422), DEFAULT));
    }

    @Test
    void networkFailureWithoutStatusIsRetryable() {
        // 执行器通用 catch：连接/读超时、连接重置、EOF 等，错误码 UPSTREAM_ERROR 且无 HTTP 状态
        GatewayUpstreamException network = new GatewayUpstreamException("connect timed out",
                ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        assertTrue(RetryableClassifier.retryable(network, DEFAULT));
    }

    @Test
    void configurationErrorsAreNotRetryable() {
        assertFalse(RetryableClassifier.retryable(
                new GatewayUpstreamException("model not found", ProxyErrorCodeEnum.MODEL_NOT_FOUND.code()), DEFAULT));
        assertFalse(RetryableClassifier.retryable(
                new GatewayUpstreamException("provider not found", ProxyErrorCodeEnum.PROVIDER_NOT_FOUND.code()), DEFAULT));
        assertFalse(RetryableClassifier.retryable(
                new GatewayUpstreamException("no protocol", ProxyErrorCodeEnum.API_PROTOCOL_NOT_CONFIGURED.code()), DEFAULT));
    }

    @Test
    void disabledOrSingleAttemptPolicyNeverRetries() {
        RetryPolicy disabled = new RetryPolicy(false, 3, 200, 5_000, 2.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE);
        RetryPolicy single = new RetryPolicy(true, 1, 200, 5_000, 2.0, true, true,
                RetryPolicy.MODE_ALLOWLIST, List.of("500"), RetryPolicy.MODE_SIMPLE);
        assertFalse(RetryableClassifier.retryable(error(500), disabled));
        assertFalse(RetryableClassifier.retryable(error(500), single));
    }

    @Test
    void denylistSkipsListedStatuses() {
        RetryPolicy denylist = new RetryPolicy(true, 3, 200, 5_000, 2.0, false, false,
                RetryPolicy.MODE_DENYLIST, List.of("400", "401"), RetryPolicy.MODE_SIMPLE);
        assertFalse(RetryableClassifier.retryable(error(400), denylist));
        assertTrue(RetryableClassifier.retryable(error(500), denylist));
    }

    @Test
    void nullErrorIsNeverRetryable() {
        assertFalse(RetryableClassifier.retryable(null, DEFAULT));
    }

    private static GatewayUpstreamException error(int status) {
        return new GatewayUpstreamException("upstream responded with status " + status,
                ProxyErrorCodeEnum.UPSTREAM_ERROR.code(), status, "{}");
    }
}
