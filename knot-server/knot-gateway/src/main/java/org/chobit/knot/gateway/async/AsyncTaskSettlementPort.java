package org.chobit.knot.gateway.async;

import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;

/**
 * 结算与事件端口：算用量 + 记限额 + 发流量事件。
 * 用量计算必须发生在落终态之前：{@link #charge} 与 {@link #publishTerminal} 是两次调用，算钱抛异常时任务不落终态，发布事件失败不影响终态。
 * 实现方在 gateway 侧（计费管线 {@code BillingService} 与流量事件 Kafka 是进程级基础设施）；编排层只关心「先算钱、再落终态、最后发事件」的顺序。
 */
public interface AsyncTaskSettlementPort {

    /**
     * 用查询响应体算一次用量。
     *
     * @return 用量；{@code null} 表示无法计费（模型不存在 / 找不到提取器），不是零用量
     * @throws RuntimeException 计算失败：编排层不落终态，下一轮重试
     */
    UsageAccounting charge(AsyncTaskDto task, AsyncTaskContext context, String queryBody);

    /**
     * 终态落库成功后，按实际用量累计一次限额 / TPM。
     * 调用点必须是终态 CAS 成功之后（保证「一笔任务只记一次」和「失败 / 超时不记」），提交阶段不得调用。
     *
     * @param usage 成功终态的实际用量；失败 / 超时为 {@code null}，由实现方直接跳过
     */
    void recordUsage(AsyncTaskDto task, AsyncTaskContext context, UsageAccounting usage);

    /**
     * 发布一次中间查询事件（非终态）；每次查询各发一条（一次异步调用在上游是「1 次提交 + N 次查询」）。
     *
     * @param querySeq  第几次查询（从 1 开始，对应行上的 {@code attempts}）
     * @param errorCode 本次查询是否出错（拿到有效响应时为 null）
     */
    void publishQuery(AsyncTaskDto task, AsyncTaskContext context, int querySeq, String errorCode);

    /**
     * 发布终态事件（一条任务一条，{@code task_terminal=true}）。
     *
     * @param usage        成功的用量；失败/超时为 null
     * @param errorCode    失败原因码；成功为 null
     * @param errorMessage 失败话术；成功为 null
     */
    void publishTerminal(AsyncTaskDto task, AsyncTaskContext context, int querySeq,
                         UsageAccounting usage, String errorCode, String errorMessage);
}
