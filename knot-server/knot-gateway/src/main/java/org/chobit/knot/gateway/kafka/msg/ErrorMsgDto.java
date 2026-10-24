package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.GatewayBadRequestException;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.exception.GatewayTrafficRejectException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.TrafficRejectDetail;
import org.chobit.knot.gateway.redis.RedisUnavailableException;

import java.util.concurrent.TimeoutException;

/**
 * 响应层的统一错误结构：<b>上游异常与网关异常同形状</b>，靠 {@code type} / {@code stage} / {@code upstream} 区分。
 *
 * <p>拒绝与失败经常被混为一谈，而它们的处置完全不同（429 要退避重试、502 要换目标、
 * 503 要修 Redis），因此一个出口必须能把三者明确分开。这里与
 * {@code GatewayExceptionHandler} 的错误映射一一对应——数仓看到的错误语义，
 * 与调用方 HTTP 响应里看到的必须是同一套。</p>
 *
 * @param detail   仅 429（限流 / 限额）携带的结构化拒绝明细
 * @param upstream 仅 502（上游失败）携带的上游响应信息
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ErrorMsgDto(String type,
                          String code,
                          int httpStatus,
                          String stage,
                          String message,
                          TrafficRejectMsgDto detail,
                          UpstreamErrorMsgDto upstream) {

    private static final int MAX_MESSAGE_LENGTH = 512;

    /**
     * 由异常实例构建错误记录；{@code null} 入参返回 {@code null}（成功路径不带 error）。
     *
     * <p>错误类型单一来源：网关异常家族在构造时已把 {@link GatewayErrorTypeEnum} 的 code
     * 写进 {@code type()}，这里直接取用，不再按异常类型二次推断——与
     * {@code GatewayExceptionHandler} 的出口保持同一套语义，也不会出现两侧各写一份
     * 字面量导致漂移。非家族异常（业务异常 / 存储不可用 / 未预期异常）没有 type，
     * 按处置语义取枚举常量。</p>
     */
    public static ErrorMsgDto of(Throwable error) {
        if (error == null) {
            return null;
        }
        // 请求预算超时（B2 deadline，Mono.timeout 抛 TimeoutException）：与 GatewayExceptionHandler
        // 的 handleRequestBudgetExceeded 同一口径（504 + timeout_error + stage=timeout）。
        // 这是「唯一真总超时」的终态，不是未预期异常——落进 500 兜底会让数仓把超时当成内部故障
        if (error instanceof TimeoutException e) {
            return new ErrorMsgDto(
                GatewayErrorTypeEnum.TIMEOUT_ERROR.code(), null, 504, Stage.TIMEOUT.code(),
                truncate(e.getMessage()), null, null);
        }
        if (error instanceof GatewayUpstreamException e) {
            return new ErrorMsgDto(
                e.type(), e.code(), 502, Stage.UPSTREAM_CALL.code(), truncate(e.getMessage()), null,
                new UpstreamErrorMsgDto(e.httpStatus(), e.code(), e.retryAfterMs()));
        }
        if (error instanceof GatewayTrafficRejectException e) {
            TrafficRejectDetail reject = e.detail();
            return new ErrorMsgDto(
                e.type(), e.code(), 429, stageOfRejection(reject), truncate(e.getMessage()),
                TrafficRejectMsgDto.of(reject), null);
        }
        if (error instanceof RedisUnavailableException e) {
            return new ErrorMsgDto(
                GatewayErrorTypeEnum.STORAGE_UNAVAILABLE.code(), null, 503, Stage.STORAGE.code(),
                truncate(e.getMessage()), null, null);
        }
        if (error instanceof GatewayAuthException e) {
            return new ErrorMsgDto(
                e.type(), e.code(), 401, Stage.HEADER_VALIDATE.code(), truncate(e.getMessage()), null, null);
        }
        if (error instanceof GatewayInvalidRequestException e) {
            return new ErrorMsgDto(
                e.type(), e.code(), 400, Stage.HEADER_VALIDATE.code(),
                truncate(e.getMessage()), null, null);
        }
        if (error instanceof GatewayBadRequestException e) {
            // 敏感词等信息：只带分类 code，不暴露命中词与词库原文
            return new ErrorMsgDto(
                e.errorType(), e.code(), 400, Stage.PLUGIN_MODERATION.code(),
                truncate(e.getMessage()), null, null);
        }
        if (error instanceof BusinessException e) {
            return new ErrorMsgDto(
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code(), e.getCode(), 400, Stage.REQUEST_PARSE.code(),
                truncate(e.getMessage()), null, null);
        }
        // 未预期异常：与 GatewayExceptionHandler 的兜底出口保持一致（500 + upstream_error）
        return new ErrorMsgDto(
            GatewayErrorTypeEnum.UPSTREAM_ERROR.code(), null, 500, Stage.INTERNAL.code(),
            truncate(error.getMessage()), null, null);
    }

    /**
     * 拒绝发生的阶段：规则级检查（应用 / 规则 / 消费者）与目标级检查（模型 / 供应商）分开。
     */
    private static String stageOfRejection(TrafficRejectDetail detail) {
        if (detail == null) {
            return Stage.ROUTING_TRAFFIC_CHECK.code();
        }
        String resourceType = detail.resourceType();
        if (TrafficResourceTypeEnum.MODEL.code().equals(resourceType)
            || TrafficResourceTypeEnum.PROVIDER.code().equals(resourceType)) {
            return Stage.TARGET_TRAFFIC_CHECK.code();
        }
        return Stage.ROUTING_TRAFFIC_CHECK.code();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }

    /**
     * 失败阶段：当前由异常类型推定，未来若异常家族自带阶段则以自带值为准。
     */
    private enum Stage {
        HEADER_VALIDATE("header_validate"),
        REQUEST_PARSE("request_parse"),
        ROUTING_TRAFFIC_CHECK("routing_traffic_check"),
        TARGET_TRAFFIC_CHECK("target_traffic_check"),
        UPSTREAM_CALL("upstream_call"),
        PLUGIN_MODERATION("plugin_moderation"),
        STORAGE("storage"),
        TIMEOUT("timeout"),
        INTERNAL("internal");

        private final String code;

        Stage(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
