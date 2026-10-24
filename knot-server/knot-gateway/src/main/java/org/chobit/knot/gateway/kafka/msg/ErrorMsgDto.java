package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.gateway.exception.GatewayTrafficRejectException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.GatewayErrorDescriptor;

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
        // type / code / httpStatus / stage 全部取自唯一映射，本类只补数仓专属的两个附件
        // （上游响应摘要、拒绝明细）——新增异常类型时改 GatewayErrorDescriptor 一处即可
        GatewayErrorDescriptor descriptor = GatewayErrorDescriptor.of(error);
        if (error instanceof GatewayUpstreamException e) {
            return new ErrorMsgDto(
                descriptor.type(), descriptor.code(), descriptor.httpStatus(), descriptor.stage(),
                truncate(e.getMessage()), null,
                new UpstreamErrorMsgDto(e.httpStatus(), e.code(), e.retryAfterMs()));
        }
        if (error instanceof GatewayTrafficRejectException e) {
            return new ErrorMsgDto(
                descriptor.type(), descriptor.code(), descriptor.httpStatus(), descriptor.stage(),
                truncate(e.getMessage()), TrafficRejectMsgDto.of(e.detail()), null);
        }
        return new ErrorMsgDto(
            descriptor.type(), descriptor.code(), descriptor.httpStatus(), descriptor.stage(),
            truncate(error.getMessage()), null, null);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
