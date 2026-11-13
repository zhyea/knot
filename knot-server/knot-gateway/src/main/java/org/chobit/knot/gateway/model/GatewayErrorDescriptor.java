package org.chobit.knot.gateway.model;

import org.chobit.knot.common.constants.enums.GatewayErrorStageEnum;
import org.chobit.knot.common.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.common.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.GatewayBadRequestException;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.exception.GatewayTrafficRejectException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.redis.RedisUnavailableException;

import java.util.concurrent.TimeoutException;

/**
 * 网关错误的唯一映射：一次失败用什么 {@code type} / {@code code} / {@code httpStatus} / {@code stage} 表达，由本类决定；HTTP 出口与数仓出口共用。
 * 不覆盖 Spring 框架异常（如 {@code ServerWebInputException}），它们只出现在 HTTP 入口，仍由 {@code GatewayExceptionHandler} 处理。
 *
 * @param type       错误性质（{@link GatewayErrorTypeEnum} 的 code）
 * @param code       具体错误码，无细分时为 {@code null}
 * @param httpStatus HTTP 状态码
 * @param stage      失败阶段（{@link GatewayErrorStageEnum} 的 code），仅数仓出口消费
 */
public record GatewayErrorDescriptor(String type,
                                     String code,
                                     int httpStatus,
                                     String stage) {

    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_BAD_GATEWAY = 502;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;
    private static final int HTTP_GATEWAY_TIMEOUT = 504;
    private static final int HTTP_INTERNAL_ERROR = 500;

    /**
     * 由异常实例解析错误描述。
     * 网关异常家族直接取 {@code type()} 中的 {@link GatewayErrorTypeEnum} code；非家族异常按处置语义取枚举常量。
     */
    public static GatewayErrorDescriptor of(Throwable error) {
        // 请求预算超时（B2 deadline，Mono.timeout 抛 TimeoutException）：这是「唯一真总超时」
        // 的终态，不是未预期异常——落进 500 兜底会让数仓把超时当成内部故障
        if (error instanceof TimeoutException) {
            return new GatewayErrorDescriptor(
                GatewayErrorTypeEnum.TIMEOUT_ERROR.code(), null,
                HTTP_GATEWAY_TIMEOUT, GatewayErrorStageEnum.TIMEOUT.code());
        }
        if (error instanceof GatewayUpstreamException e) {
            // HTTP 状态码保持 502（错误码隔离）：上游原状态码另行透进响应体，不在状态码上透出
            return new GatewayErrorDescriptor(
                e.type(), e.code(), HTTP_BAD_GATEWAY, GatewayErrorStageEnum.UPSTREAM_CALL.code());
        }
        if (error instanceof GatewayTrafficRejectException e) {
            return new GatewayErrorDescriptor(
                e.type(), e.code(), HTTP_TOO_MANY_REQUESTS, stageOfRejection(e.detail()));
        }
        if (error instanceof RedisUnavailableException) {
            // 刻意不复用 UPSTREAM_ERROR：上游不可用是「模型服务的问题」，存储不可用是
            // 「网关自己的 Redis 挂了」，处置完全不同（一个可换节点，一个必须先修 Redis）
            return new GatewayErrorDescriptor(
                GatewayErrorTypeEnum.STORAGE_UNAVAILABLE.code(), null,
                HTTP_SERVICE_UNAVAILABLE, GatewayErrorStageEnum.STORAGE.code());
        }
        if (error instanceof GatewayAuthException e) {
            return new GatewayErrorDescriptor(
                e.type(), e.code(), HTTP_UNAUTHORIZED, GatewayErrorStageEnum.HEADER_VALIDATE.code());
        }
        if (error instanceof GatewayInvalidRequestException e) {
            return new GatewayErrorDescriptor(
                e.type(), e.code(), HTTP_BAD_REQUEST, GatewayErrorStageEnum.HEADER_VALIDATE.code());
        }
        if (error instanceof GatewayBadRequestException e) {
            // 敏感词等信息：只带分类 code，不暴露命中词与词库原文
            return new GatewayErrorDescriptor(
                e.errorType(), e.code(), HTTP_BAD_REQUEST, GatewayErrorStageEnum.PLUGIN_MODERATION.code());
        }
        if (error instanceof BusinessException e) {
            return new GatewayErrorDescriptor(
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code(), e.getCode(),
                HTTP_BAD_REQUEST, GatewayErrorStageEnum.REQUEST_PARSE.code());
        }
        // 兜底：网关自己的未预期异常。刻意不复用 UPSTREAM_ERROR —— 上游错误是「模型服务回的」，
        // 这里是我们自己炸了；混记会让数仓的上游错误率凭空变高，排查也会被引到供应商那边
        return new GatewayErrorDescriptor(
            GatewayErrorTypeEnum.INTERNAL_ERROR.code(), null,
            HTTP_INTERNAL_ERROR, GatewayErrorStageEnum.INTERNAL.code());
    }

    /**
     * 拒绝发生的阶段：规则级检查（应用 / 规则 / 消费者）与目标级检查（模型 / 供应商）分开。
     */
    private static String stageOfRejection(TrafficRejectDetail detail) {
        if (detail == null) {
            return GatewayErrorStageEnum.ROUTING_TRAFFIC_CHECK.code();
        }
        String resourceType = detail.resourceType();
        if (TrafficResourceTypeEnum.MODEL.code().equals(resourceType)
            || TrafficResourceTypeEnum.PROVIDER.code().equals(resourceType)) {
            return GatewayErrorStageEnum.TARGET_TRAFFIC_CHECK.code();
        }
        return GatewayErrorStageEnum.ROUTING_TRAFFIC_CHECK.code();
    }
}
