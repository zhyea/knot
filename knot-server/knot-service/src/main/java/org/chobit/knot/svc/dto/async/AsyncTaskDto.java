package org.chobit.knot.svc.dto.async;

import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;

import java.time.LocalDateTime;

/**
 * 一条异步任务实例的<b>读出</b>模型（对应 {@code kb_async_tasks} 一行）。
 *
 * <p>⭐ <b>为什么要有这么一个类，而不是把 {@code AsyncTaskEntity} 直接交给 gateway</b>：
 * 分层红线是「只有 {@code knot-service} 能碰 Mapper」，而如果 svc 把 dal 实体原样吐出去，
 * gateway 就得 import {@code org.chobit.knot.dal.entity} 才能读字段——红线从「禁止碰 Mapper」
 * 退化成「禁止碰 Mapper 但可以碰 Entity」，等于给 dal 留了一扇后门，下次有人直接把实体
 * 塞进另一个 mapper 调用里也没有编译信号。出一层模型，gateway 对 dal 的依赖就是零。</p>
 *
 * <p>⚠ <b>刻意不含</b> {@code claimExpiresAt} / {@code lastPolledAt} / {@code createdAt} /
 * {@code updatedAt}：这四个是存储内部字段（占位回收与审计），消费方（gateway 的轮询器与查询端点）
 * 一个都不用。字段按「谁在读」收，而不是按「表里有什么」抄——后者会让模型随表一起膨胀。</p>
 *
 * <p>三个 JSON 段（{@code requestJson} / {@code pollSpecJson} / {@code contextJson}）在模型里
 * 仍以<b>文本</b>承载，与实体一致：序列化策略由消费方（{@code JsonKit}）唯一决定，
 * 这里不做反序列化，避免「同一个字段两种序列化口径」。</p>
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
                               * 重试轮次（0 = 从未被管理端重试）。
                               *
                               * <p>⚠ 它只出现在<b>详情 / 终态事件</b>里，轮询编排不用它：
                               * 状态机只认状态码。存在的理由是「重试会把 attempts 归零」，
                               * 于是 {@code task_query_seq} 从 1 重来，与上一轮同键——
                               * 轮次是数仓区分两轮的唯一坐标。</p>
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
                               *
                               * <p>⭐ 它是管理端离线重算<b>唯一</b>的上游输入（视频类提取器从响应取
                               * 输出时长与产物数）。与 {@code resultJson} 分开：后者是对外契约。</p>
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
                               *
                               * <p>⚠ 与 {@code status} 是两个维度：{@code SUCCEEDED} 只说明生成成功，
                               * 不代表这个地址已经收到过结果。</p>
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
