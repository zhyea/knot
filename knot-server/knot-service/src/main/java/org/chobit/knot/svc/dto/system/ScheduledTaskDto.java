package org.chobit.knot.svc.dto.system;

import java.time.LocalDateTime;

/**
 * 定时任务定义（对应 {@code ks_scheduled_tasks} 一行），由 {@code ScheduledTaskController} 直接序列化为对外 JSON，
 * 前端按字段名消费，字段不可随意收窄。
 * {@code status} 为 {@code EnabledStatusEnum.code()}（1-启用 / 0-停用），与异步任务状态码是两套命名空间。
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
