package org.chobit.knot.gateway.outage;

import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.EjectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：哪些上游失败算「模型自身的异常」——决定是否计入摘除判定。
 */
class OutageClassifierTest {

    private static final EjectionPolicy ENABLED = new EjectionPolicy(
            true, 60, 5, true, 15_000, 60_000,
            true, 2.0, 600_000, 30_000, true, EjectionPolicy.MODE_SIMPLE);

    private static final EjectionPolicy DISABLED = new EjectionPolicy(
            false, 60, 5, true, 15_000, 60_000,
            true, 2.0, 600_000, 30_000, true, EjectionPolicy.MODE_SIMPLE);

    @Test
    @DisplayName("5xx 与上游 429 计数")
    void serverErrorsAndUpstream429Count() {
        assertTrue(OutageClassifier.countable(upstream(500, "UPSTREAM_ERROR"), ENABLED));
        assertTrue(OutageClassifier.countable(upstream(503, "UPSTREAM_ERROR"), ENABLED));
        assertTrue(OutageClassifier.countable(upstream(429, "UPSTREAM_ERROR"), ENABLED));
    }

    @Test
    @DisplayName("4xx 业务错误不计数")
    void clientErrorsDoNotCount() {
        assertFalse(OutageClassifier.countable(upstream(400, "UPSTREAM_ERROR"), ENABLED));
        assertFalse(OutageClassifier.countable(upstream(401, "UPSTREAM_ERROR"), ENABLED));
        assertFalse(OutageClassifier.countable(upstream(404, "UPSTREAM_ERROR"), ENABLED));
        // 429 是唯一例外（上游限流）
        assertTrue(OutageClassifier.countable(upstream(429, "UPSTREAM_ERROR"), ENABLED));
    }

    @Test
    @DisplayName("配置类错误不计数")
    void configurationErrorsDoNotCount() {
        for (String code : new String[]{
                ProxyErrorCodeEnum.MODEL_NOT_FOUND.code(),
                ProxyErrorCodeEnum.PROVIDER_NOT_FOUND.code(),
                ProxyErrorCodeEnum.API_PROTOCOL_NOT_CONFIGURED.code()}) {
            assertFalse(OutageClassifier.countable(upstream(null, code), ENABLED), code);
        }
    }

    @Test
    @DisplayName("无状态时只有网络/传输层失败计数")
    void networkLevelOnlyWhenNoStatus() {
        assertTrue(OutageClassifier.countable(upstream(null, "UPSTREAM_ERROR"), ENABLED));
        assertFalse(OutageClassifier.countable(upstream(null, "NO_ROUTING_TARGET"), ENABLED));
    }

    @Test
    @DisplayName("策略关闭时一律不计数")
    void disabledPolicyCountsNothing() {
        assertFalse(OutageClassifier.countable(upstream(500, "UPSTREAM_ERROR"), DISABLED));
        assertFalse(OutageClassifier.countable(null, ENABLED));
    }

    private static GatewayUpstreamException upstream(Integer status, String code) {
        return new GatewayUpstreamException("upstream failed", code, status, "{}", null);
    }
}
