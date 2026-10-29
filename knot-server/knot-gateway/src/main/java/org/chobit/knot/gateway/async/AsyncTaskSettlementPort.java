package org.chobit.knot.gateway.async;

import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;

/**
 * 结算与事件端口：算用量 + 发流量事件。
 *
 * <p>⭐ <b>为什么这两件事在一个端口里</b>：它们共用同一个装配前提（模型、路由、trace、
 * 客户端原始请求体）。拆成两个端口就要把「怎么重建上下文」写两遍，
 * 而两份实现必然漂移——漂移的表现是「终态事件有模型、中间查询事件没有」，数仓 join 时静默丢行。
 * 这是既有 {@code AsyncTaskCompletion} 里 {@code publish} 私有方法的教训。</p>
 *
 * <p>⚠ <b>用量计算必须发生在落终态之前</b>，所以 {@link #charge} 与 {@link #publishTerminal}
 * 是两次调用而不是一个方法：算钱抛异常时任务不能落终态（否则这笔调用的钱永远收不回来），
 * 而发布事件失败<b>不能</b>影响终态（钱已经算过，事件是旁路观测）。
 * 合成一个方法就无法同时满足这两条相反的失败语义。</p>
 *
 * <p>⚠ 实现方在 gateway 侧，因为计费管线（`BillingService`）与流量事件（Kafka）是进程级基础设施；
 * 编排层只关心「要先算钱、再落终态、最后发事件」这个顺序。</p>
 */
public interface AsyncTaskSettlementPort {

    /**
     * 用查询响应体算一次用量。
     *
     * @return 用量；{@code null} 表示<b>无法计费</b>（模型不存在 / 找不到提取器），不是零用量
     * @throws RuntimeException 计算失败：编排层不落终态，下一轮重试
     */
    UsageAccounting charge(AsyncTaskDto task, AsyncTaskContext context, String queryBody);

    /**
     * 发布一次中间查询事件（非终态）。
     *
     * <p>⭐ 一次异步调用在上游是「1 次提交 + N 次查询」，N 可达数百。只发终态那条等于把
     * N-1 次上游调用从数仓里抹掉——上游成本算不出、产物延迟分布算不出、
     * 「重试 5 次才成功」会被压平成「1 次成功」。</p>
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
