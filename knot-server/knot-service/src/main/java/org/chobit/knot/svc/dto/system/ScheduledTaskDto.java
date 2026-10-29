package org.chobit.knot.svc.dto.system;

import java.time.LocalDateTime;

/**
 * 定时任务定义（对应 {@code ks_scheduled_tasks} 一行）。
 *
 * <p>⭐ <b>为什么字段与 {@code ScheduledTaskEntity} 完全一致，不像 {@code AsyncTaskDto}
 * 那样按「谁在读」收</b>：两者的角色不同。{@code AsyncTaskDto} 是<b>内部存储模型</b>
 * （svc → gateway 内部使用），收窄字段是收益；而本 record 由
 * {@code ScheduledTaskController} <b>直接序列化成对外 JSON</b>，前端三个组件按字段名消费。
 * 少一个 {@code createdAt}，接口响应就静默变样，而后端全绿、测试也全绿——
 * 那是契约被改了却没有任何人被告知。</p>
 *
 * <p>⚠ 因此 <b>对外契约类的收放标准与内部模型相反</b>：对外的一律先对齐、再单独走契约变更流程收窄；
 * 内部的可以按消费方收。别把这条当「字段越多越安全」——它只适用于跨出进程边界的那一类。</p>
 *
 * <p>{@code status} 是 {@code EnabledStatusEnum.code()}（1-启用 / 0-停用），
 * 与异步任务的状态码是两套命名空间，不要互相套用。</p>
 */
public record ScheduledTaskDto(Long id,
                                  String taskCode,
                                  String taskName,
                                  String handlerCode,
                                  String cronExpression,
                                  /** {@code SINGLE}（单节点执行，靠 DB 锁互斥）/ {@code BROADCAST}（每个节点各跑一次） */
                                  String executionMode,
                                  Integer status,
                                  String description,
                                  LocalDateTime lastFireAt,
                                  LocalDateTime nextFireAt,
                                  LocalDateTime createdAt,
                                  LocalDateTime updatedAt) {
}
