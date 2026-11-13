package org.chobit.knot.svc.dto.system;

import java.time.LocalDateTime;

/**
 * 定时任务的一次执行记录（对应 {@code ks_scheduled_task_runs} 一行），由 controller 直接序列化。
 * 既是读出模型也是写入入参：{@code insertRun} 插入时即带 {@code status=RUNNING}、{@code startTime} 与执行模式等字段。
 * {@code status} 为 {@code ScheduledTaskRunStatusEnum.code()}（0-执行中 / 1-成功 / 2-失败）。
 */
public record ScheduledTaskRunDto(Long id,
                                     Long taskId,
                                     String taskCode,
                                     String taskName,
                                     String executionMode,
                                     /** 执行节点（hostname-pid），用于排查「哪个节点跑了这一趟」 */
                                     String nodeId,
                                     /** {@code SCHEDULE}（cron 触发）/ {@code MANUAL}（手动触发） */
                                     String triggerType,
                                     Integer status,
                                     LocalDateTime startTime,
                                     LocalDateTime endTime,
                                     Long durationMs,
                                     Integer affectedRows,
                                     String errorMsg,
                                     LocalDateTime createdAt) {
}
