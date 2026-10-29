package org.chobit.knot.admin.service.schedule;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ScheduledTaskRunStatusEnum;
import org.chobit.knot.svc.dto.system.ScheduledTaskDto;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunDto;
import org.chobit.knot.svc.service.ScheduledTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务执行引擎：抢锁 → 调 handler → 记流水。
 *
 * <p>⭐ <b>为什么留在 admin</b>：它要收集 {@link ScheduledTaskHandler} 的实现
 * （{@code OPENROUTER_MODEL_SYNC} 等），这些实现只存在于管理端。数据访问已收进 svc 的
 * {@link ScheduledTaskService}。</p>
 *
 * <p>⚠ <b>SINGLE 模式的互斥靠 DB 会话锁</b>（{@code GET_LOCK}），不是进程内锁：
 * 多节点同时到点时只有一个节点能拿到锁，其余直接跳过（不排队、不补跑）。
 * {@code BROADCAST} 模式刻意<b>不加锁</b>——它的语义就是「每个节点各跑一次」。</p>
 */
@Slf4j
@Service
public class ScheduledTaskExecutor {

    private static final String MODE_SINGLE = "SINGLE";
    private static final Integer STATUS_RUNNING = ScheduledTaskRunStatusEnum.RUNNING.code();
    private static final Integer STATUS_SUCCESS = ScheduledTaskRunStatusEnum.SUCCESS.code();
    private static final Integer STATUS_FAILURE = ScheduledTaskRunStatusEnum.FAILURE.code();

    private final ScheduledTaskService taskService;
    private final NodeIdentity nodeIdentity;
    private final Map<String, ScheduledTaskHandler> handlers;

    /**
     * Constructs a new instance.
     */
    public ScheduledTaskExecutor(ScheduledTaskService taskService,
                                 NodeIdentity nodeIdentity,
                                 List<ScheduledTaskHandler> handlers) {
        this.taskService = taskService;
        this.nodeIdentity = nodeIdentity;
        this.handlers = handlers.stream().collect(Collectors.toMap(ScheduledTaskHandler::handlerCode, Function.identity()));
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Transactional
    public void execute(String taskCode, String triggerType, Date nextFireTime) {
        ScheduledTaskDto task = taskService.getTaskByCode(taskCode);
        if (task == null || !EnabledStatusEnum.isEnabled(task.status())) {
            return;
        }

        String lockName = "knot:scheduled-task:" + task.taskCode();
        boolean locked = false;
        try {
            if (MODE_SINGLE.equals(task.executionMode())) {
                locked = taskService.acquireLock(lockName);
                if (!locked) {
                    log.info("Scheduled task skipped because another node owns the lock: {}", taskCode);
                    return;
                }
            }
            executeLocked(task, triggerType, toLocalDateTime(nextFireTime));
        } finally {
            if (locked) {
                taskService.releaseLock(lockName);
            }
        }
    }

    private void executeLocked(ScheduledTaskDto task, String triggerType, LocalDateTime nextFireAt) {
        LocalDateTime start = LocalDateTime.now();
        Long runId = taskService.insertRun(new ScheduledTaskRunDto(
                null,
                task.id(),
                task.taskCode(),
                task.taskName(),
                task.executionMode(),
                nodeIdentity.nodeId(),
                triggerType,
                STATUS_RUNNING,
                start,
                null,
                null,
                0,
                null,
                null));

        try {
            ScheduledTaskHandler handler = handlers.get(task.handlerCode());
            if (handler == null) {
                throw new IllegalStateException("Unknown scheduled task handler: " + task.handlerCode());
            }
            ScheduledTaskResult result = handler.execute();
            finishRun(runId, STATUS_SUCCESS, result.affectedRows(), result.message(), start);
            taskService.updateTaskFireTime(task.id(), start, nextFireAt);
        } catch (Exception e) {
            log.warn("Scheduled task failed: {}", task.taskCode(), e);
            finishRun(runId, STATUS_FAILURE, 0, e.getMessage(), start);
        }
    }

    private void finishRun(Long runId, Integer status, Integer affectedRows, String message, LocalDateTime start) {
        LocalDateTime end = LocalDateTime.now();
        // ⚠ 成功记录不带 errorMsg：否则「有错误消息」不再等于「失败过」，运维按它筛就是错的
        String errorMsg = STATUS_FAILURE.equals(status) ? truncate(message, 1000) : null;
        taskService.finishRun(runId, status, end, Duration.between(start, end).toMillis(),
                affectedRows, errorMsg);
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
