package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 响应层 {@code attempts[]} 的一行：一次上游调用尝试。
 *
 * @param seq                序号，从 1 开始（候选循环里的登记顺序）
 * @param upstreamModelCode  实际发往上游的模型标识；网关会用它覆盖请求体 {@code model}
 * @param outcome            结局，见 {@link AttemptOutcomeMsg}
 * @param attempts           本候选实际试投次数（含首次）
 * @param countedForEjection 该次失败是否计入摘除判定；只有它为 true 的 5xx/429 才算「上游故障」
 * @param upstreamRequestId  供应商侧请求 id（响应头白名单归一）：成功失败均带，供联合排查检索；
 *                           {@code attempt_no=0}（未发起调用即被拒）或上游无匹配头为 null
 * @param upstreamUrl        本次上游调用的完整请求地址（scheme + host + path）：成功失败均带，
 *                           排障能直接看出这个候选打到了哪个供应商端点；未发起调用（频控拒绝）为 null
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
                            String upstreamRequestId,
                            boolean countedForEjection,
                            String upstreamUrl) {
}
