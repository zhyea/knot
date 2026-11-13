package org.chobit.knot.svc.dto.async;

import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;

import java.time.LocalDateTime;

/**
 * 一条异步任务实例的读出模型（对应 {@code kb_async_tasks} 一行）。
 *
 * 不含 {@code claimExpiresAt} / {@code lastPolledAt} / {@code createdAt} / {@code updatedAt} 等存储内部字段。
 * {@code requestJson} / {@code pollSpecJson} / {@code contextJson} 以文本承载，序列化策略由消费方 {@code JsonKit} 决定。
 */
public record AsyncTaskDto(Long id,
                              /** 对外任务句柄（客户端轮询用，非主键 id） */
                              String taskCode,
                              String profileCode,
                              String protocol,
                              String upstreamTaskId,
                              /** 落库状态码，见 {@link AsyncTaskStatusEnum#code()} */
                              Integer status,
                              Integer attempts,
                              /**
                               * 重试轮次（0 = 从未被管理端重试）。只出现在详情 / 终态事件，轮询编排只用状态码。
                               * 重试会把 {@code attempts} 归零、{@code task_query_seq} 从 1 重来，轮次用于区分两轮。
                               */
                              Integer retryRound,
                              /** 下次轮询时刻；终态为 null，天然退出扫描集 */
                              LocalDateTime nextPollAt,
                              /** 轮询占位标识（nodeId-uuid）；写操作必须带着它，见 {@link org.chobit.knot.svc.service.AsyncTaskService} */
                              String claimId,
                              String requestJson,
                              String pollSpecJson,
                              String contextJson,
                              String resultJson,
                              /**
                               * 上游终态查询响应原文；未到终态或无响应时为 {@code null}。
                               * 管理端离线重算的唯一上游输入；{@code resultJson} 才是对外的结果契约。
                               */
                              String terminalBody,
                              /**
                               * 终态结算快照（{@code knot_extend} 同形 {@code {usage, billing}}）；
                               * 失败/超时/取消不计费 → {@code null}。
                               */
                              String settlementJson,
                              String errorCode,
                              String errorMessage,
                              String billingRuleCode,
                              String billingRuleVersionCode,
                              LocalDateTime submittedAt,
                              LocalDateTime completedAt,
                              LocalDateTime expiresAt,
                              LocalDateTime resultExpiresAt,
                              // ───────────── 回调交付维度（与 status 正交） ─────────────
                              /**
                               * 客户端回推地址；{@code null} 表示不回调。
                               * 与 {@code status} 是两个维度：{@code SUCCEEDED} 只说明生成成功，
                               * 不代表这个地址已经收到过结果。
                               */
                              String callbackUrl,
                              /** 交付状态码（{@link org.chobit.knot.common.constants.enums.AsyncTaskDeliveryStatusEnum#code()}） */
                              Integer deliveryStatus,
                              /** 已尝试投递次数（含失败重试） */
                              Integer deliveryAttempts,
                              /** 下一次投递时刻；{@code null} 表示不在待投递集（未终态 / 已交付 / 已放弃） */
                              LocalDateTime nextDeliveryAt,
                              /** 成功送达时刻；未送达为 {@code null} */
                              LocalDateTime deliveredAt,
                              /** 最近一次投递失败原因（对端响应或连接错误摘要） */
                              String deliveryError) {
}
