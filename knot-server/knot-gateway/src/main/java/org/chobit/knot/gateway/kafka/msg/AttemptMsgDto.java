package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.chobit.knot.gateway.kafka.AttemptOutcomeMsg;

/**
 * 响应层 {@code attempts[]} 的一行：一次上游调用尝试。
 *
 * @param seq                序号，从 1 开始（候选循环里的登记顺序）
 * @param upstreamModelCode  实际发往上游的模型标识；网关会用它覆盖请求体 {@code model}
 * @param outcome            结局，见 {@link AttemptOutcomeMsg}
 * @param attempts           本候选实际试投次数（含首次）
 * @param countedForEjection 该次失败是否计入摘除判定；只有它为 true 的 5xx/429 才算「上游故障」
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AttemptMsgDto(int seq,
                            String targetCode,
                            String targetType,
                            String upstreamModelCode,
                            int priority,
                            boolean primary,
                            String outcome,
                            boolean skippedByEjection,
                            boolean probeRequest,
                            int attempts,
                            int maxAttempts,
                            boolean trafficRejected,
                            String rejectReason,
                            UpstreamErrorMsgDto upstream,
                            boolean countedForEjection) {
}
