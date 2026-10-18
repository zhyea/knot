package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

/**
 * 限流 / 限额拒绝的结构化明细：告诉数仓「谁被限了、限的哪个维度、上限与已用多少、何时恢复」。
 *
 * <p>数值口径：RPM/TPM/TOKENS 是自然单位；成本维度内部按纳元计数，
 * {@link org.chobit.knot.gateway.model.TrafficRejectDetail} 已还原成金额，
 * 这里存的就是还原后的金额（配合 {@code unit} 给出币种），不再保留纳元。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TrafficRejectMsgDto(String resourceType,
                                  String resourceCode,
                                  String reason,
                                  String dimension,
                                  String unit,
                                  BigDecimal limit,
                                  BigDecimal used,
                                  String resetAt,
                                  long resetAfterSeconds) {

    /**
     * 由网关拒绝明细构建；{@code null} 入参返回 {@code null}。
     */
    public static TrafficRejectMsgDto of(org.chobit.knot.gateway.model.TrafficRejectDetail detail) {
        if (detail == null) {
            return null;
        }
        return new TrafficRejectMsgDto(
            detail.resourceType(),
            detail.resourceCode(),
            detail.reason(),
            detail.dimension(),
            detail.unit(),
            detail.limit(),
            detail.used(),
            detail.resetAt(),
            detail.resetAfterSeconds());
    }
}
