package org.chobit.knot.svc.dto.system;

import java.time.LocalDateTime;

/**
 * 定时任务的一次执行记录（对应 {@code ks_scheduled_task_runs} 一行）。
 *
 * <p>与 {@link ScheduledTaskDto} 同理：<b>字段与实体一致，因为它被 controller 直接序列化</b>
 * （运行记录面板按字段名消费）。见 {@link ScheduledTaskDto} 类注释里那条取舍判据。</p>
 *
 * <p>⚠ 本记录<b>既是读出模型也是写入入参</b>，与异步任务那边「Record（读）+ Draft（写）分开」
 * 不同：这里的 {@code insertRun} 在插入时就要带上 {@code status=RUNNING}、{@code startTime}
 * 与执行模式等字段，写与读的字段集高度重合，分成两个模型只会让「插入之前哪些字段必然有值」
 * 这件事变成要在两处维护。</p>
 *
 * <p>{@code status} 是 {@code ScheduledTaskRunStatusEnum.code()}（0-执行中 / 1-成功 / 2-失败）。</p>
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
