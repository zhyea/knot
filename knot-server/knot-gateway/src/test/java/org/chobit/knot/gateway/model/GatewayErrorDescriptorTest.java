package org.chobit.knot.gateway.model;

import org.chobit.knot.common.constants.enums.GatewayErrorStageEnum;
import org.chobit.knot.common.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.error.GatewayBadRequestException;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.exception.GatewayQuotaException;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.redis.RedisUnavailableException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 错误映射的唯一性契约：HTTP 出口与数仓出口此前各写一棵 if-else 树，新增异常类型
 * 漏改一侧就会语义漂移（编译与单测都发现不了）。现在两侧都取 {@link GatewayErrorDescriptor}，
 * 本测试逐条钉住「异常 → type / code / httpStatus / stage」的映射。
 *
 * <p>改本类映射时必须同步想清楚：数仓统计口径与调用方看到的语义是同一份。</p>
 */
class GatewayErrorDescriptorTest {

    private static TrafficRejectDetail reject(String resourceType) {
        return new TrafficRejectDetail(resourceType, "code-1", "QUOTA_COST", "cost", "USD",
                BigDecimal.ONE, BigDecimal.ONE, null, 0L);
    }

    @Test
    void 请求预算超时映射为504与timeout阶段() {
        GatewayErrorDescriptor d = GatewayErrorDescriptor.of(new TimeoutException("deadline"));
        assertEquals(GatewayErrorTypeEnum.TIMEOUT_ERROR.code(), d.type());
        assertNull(d.code());
        assertEquals(504, d.httpStatus());
        assertEquals(GatewayErrorStageEnum.TIMEOUT.code(), d.stage());
    }

    @Test
    void 上游失败映射为502与上游调用阶段() {
        GatewayErrorDescriptor d = GatewayErrorDescriptor.of(
                new GatewayUpstreamException("boom", "UPSTREAM_ERROR"));
        assertEquals(GatewayErrorTypeEnum.UPSTREAM_ERROR.code(), d.type());
        assertEquals("UPSTREAM_ERROR", d.code());
        assertEquals(502, d.httpStatus());
        assertEquals(GatewayErrorStageEnum.UPSTREAM_CALL.code(), d.stage());
    }

    @Test
    void 限流拒绝映射为429且阶段按资源类型分流() {
        GatewayErrorDescriptor ruleLevel = GatewayErrorDescriptor.of(
                new GatewayRateLimitException("限流", "RATE_LIMIT_EXCEEDED", reject("APP")));
        assertEquals(GatewayErrorTypeEnum.RATE_LIMIT_ERROR.code(), ruleLevel.type());
        assertEquals(429, ruleLevel.httpStatus());
        assertEquals(GatewayErrorStageEnum.ROUTING_TRAFFIC_CHECK.code(), ruleLevel.stage());

        // 模型 / 供应商属于目标级检查，与规则级分开统计
        GatewayErrorDescriptor modelLevel = GatewayErrorDescriptor.of(
                new GatewayQuotaException("限额", "QUOTA_EXCEEDED", reject("MODEL")));
        assertEquals(GatewayErrorTypeEnum.QUOTA_ERROR.code(), modelLevel.type());
        assertEquals(GatewayErrorStageEnum.TARGET_TRAFFIC_CHECK.code(), modelLevel.stage());

        GatewayErrorDescriptor providerLevel = GatewayErrorDescriptor.of(
                new GatewayRateLimitException("限流", "RATE_LIMIT_EXCEEDED", reject("PROVIDER")));
        assertEquals(GatewayErrorStageEnum.TARGET_TRAFFIC_CHECK.code(), providerLevel.stage());
    }

    @Test
    void 拒绝明细缺失时按规则级检查处理() {
        GatewayErrorDescriptor d = GatewayErrorDescriptor.of(
                new GatewayRateLimitException("限流", "RATE_LIMIT_EXCEEDED", null));
        assertEquals(GatewayErrorStageEnum.ROUTING_TRAFFIC_CHECK.code(), d.stage());
        assertEquals(429, d.httpStatus());
    }

    @Test
    void 存储不可用映射为503且不复用上游错误类型() {
        GatewayErrorDescriptor d = GatewayErrorDescriptor.of(
                new RedisUnavailableException("traffic", "get", "k", null));
        assertEquals(GatewayErrorTypeEnum.STORAGE_UNAVAILABLE.code(), d.type());
        assertNull(d.code());
        assertEquals(503, d.httpStatus());
        assertEquals(GatewayErrorStageEnum.STORAGE.code(), d.stage());
    }

    @Test
    void 鉴权失败映射为401与请求头校验阶段() {
        GatewayErrorDescriptor d = GatewayErrorDescriptor.of(new GatewayAuthException("no key"));
        assertEquals(GatewayErrorTypeEnum.AUTH_ERROR.code(), d.type());
        assertEquals(401, d.httpStatus());
        assertEquals(GatewayErrorStageEnum.HEADER_VALIDATE.code(), d.stage());
    }

    @Test
    void 参数非法映射为400与请求头校验阶段() {
        GatewayErrorDescriptor d = GatewayErrorDescriptor.of(new GatewayInvalidRequestException("bad body"));
        assertEquals(GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code(), d.type());
        assertEquals(400, d.httpStatus());
        assertEquals(GatewayErrorStageEnum.HEADER_VALIDATE.code(), d.stage());
    }

    @Test
    void 插件拦截映射为400与插件阶段() {
        GatewayErrorDescriptor d = GatewayErrorDescriptor.of(
                new GatewayBadRequestException("命中敏感词", GatewayErrorTypeEnum.MODERATION_ERROR.code(), "BANNED"));
        assertEquals(GatewayErrorTypeEnum.MODERATION_ERROR.code(), d.type());
        assertEquals("BANNED", d.code());
        assertEquals(400, d.httpStatus());
        assertEquals(GatewayErrorStageEnum.PLUGIN_MODERATION.code(), d.stage());
    }

    @Test
    void 业务异常映射为400与请求解析阶段() {
        GatewayErrorDescriptor d = GatewayErrorDescriptor.of(
                new BusinessException(ErrorCode.VALIDATION_ERROR, "参数不对"));
        assertEquals(GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code(), d.type());
        assertEquals(ErrorCode.VALIDATION_ERROR.code(), d.code());
        assertEquals(400, d.httpStatus());
        assertEquals(GatewayErrorStageEnum.REQUEST_PARSE.code(), d.stage());
    }

    @Test
    void 未预期异常兜底为500与内部阶段() {
        GatewayErrorDescriptor d = GatewayErrorDescriptor.of(new IllegalStateException("unexpected"));
        assertEquals(GatewayErrorTypeEnum.UPSTREAM_ERROR.code(), d.type());
        assertNull(d.code());
        assertEquals(500, d.httpStatus());
        assertEquals(GatewayErrorStageEnum.INTERNAL.code(), d.stage());
    }
}
