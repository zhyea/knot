package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.gateway.exception.GatewayTrafficRejectException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.GatewayErrorDescriptor;

/**
 * 响应层的统一错误结构：上游异常与网关异常同形状，靠 {@code type} / {@code stage} / {@code upstream} 区分。
 * 与 {@code GatewayExceptionHandler} 的错误映射一一对应，数仓与调用方 HTTP 响应使用同一套错误语义。
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
     * 网关异常家族直接取 {@code type()} 中的 {@link GatewayErrorTypeEnum} code；非家族异常按处置语义取枚举常量。
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
