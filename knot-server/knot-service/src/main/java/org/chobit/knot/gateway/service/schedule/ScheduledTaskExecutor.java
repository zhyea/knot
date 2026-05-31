package org.chobit.knot.gateway.service.schedule;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.entity.ScheduledTaskEntity;
import org.chobit.knot.gateway.entity.ScheduledTaskRunEntity;
import org.chobit.knot.gateway.mapper.ScheduledTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScheduledTaskExecutor {

    private static final String MODE_SINGLE = "SINGLE";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILURE = "FAILURE";

    private final ScheduledTaskMapper scheduledTaskMapper;
    private final NodeIdentity nodeIdentity;
    private final Map<String, ScheduledTaskHandler> handlers;

    /**
     * Constructs a new instance.
     */
    public ScheduledTaskExecutor(ScheduledTaskMapper scheduledTaskMapper,
                                 NodeIdentity nodeIdentity,
                                 List<ScheduledTaskHandler> handlers) {
        this.scheduledTaskMapper = scheduledTaskMapper;
        this.nodeIdentity = nodeIdentity;
        this.handlers = handlers.stream().collect(Collectors.toMap(ScheduledTaskHandler::handlerCode, Function.identity()));
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Transactional
    public void execute(String taskCode, String triggerType, Date nextFireTime) {
        ScheduledTaskEntity task = scheduledTaskMapper.getTaskByCode(taskCode);
        if (task == null || !EntityStatusEnum.ENABLED.code().equals(task.getStatus())) {
            return;
        }

        String lockName = "knot:scheduled-task:" + task.getTaskCode();
        boolean locked = false;
        try {
            if (MODE_SINGLE.equals(task.getExecutionMode())) {
                locked = acquireLock(lockName);
                if (!locked) {
                    log.info("Scheduled task skipped because another node owns the lock: {}", taskCode);
                    return;
                }
            }
            executeLocked(task, triggerType, toLocalDateTime(nextFireTime));
        } finally {
            if (locked) {
                releaseLock(lockName);
            }
        }
    }

    private void executeLocked(ScheduledTaskEntity task, String triggerType, LocalDateTime nextFireAt) {
        LocalDateTime start = LocalDateTime.now();
        ScheduledTaskRunEntity run = new ScheduledTaskRunEntity();
        run.setTaskId(task.getId());
        run.setTaskCode(task.getTaskCode());
        run.setTaskName(task.getTaskName());
        run.setExecutionMode(task.getExecutionMode());
        run.setNodeId(nodeIdentity.nodeId());
        run.setTriggerType(triggerType);
        run.setStatus(STATUS_RUNNING);
        run.setStartTime(start);
        run.setAffectedRows(0);
        scheduledTaskMapper.insertRun(run);

        try {
            ScheduledTaskHandler handler = handlers.get(task.getHandlerCode());
            if (handler == null) {
                throw new IllegalStateException("Unknown scheduled task handler: " + task.getHandlerCode());
            }
            ScheduledTaskResult result = handler.execute();
            finishRun(run, STATUS_SUCCESS, result.affectedRows(), result.message(), start);
            scheduledTaskMapper.updateTaskFireTime(task.getId(), start, nextFireAt);
        } catch (Exception e) {
            log.warn("Scheduled task failed: {}", task.getTaskCode(), e);
            finishRun(run, STATUS_FAILURE, 0, e.getMessage(), start);
        }
    }

    private void finishRun(ScheduledTaskRunEntity run, String status, Integer affectedRows, String message, LocalDateTime start) {
        LocalDateTime end = LocalDateTime.now();
        run.setStatus(status);
        run.setEndTime(end);
        run.setDurationMs(java.time.Duration.between(start, end).toMillis());
        run.setAffectedRows(affectedRows);
        run.setErrorMsg(STATUS_FAILURE.equals(status) ? truncate(message, 1000) : null);
        scheduledTaskMapper.updateRun(run);
    }

    private boolean acquireLock(String lockName) {
        Integer ret = scheduledTaskMapper.acquireLock(lockName);
        return ret != null && ret == 1;
    }

    private void releaseLock(String lockName) {
        try {
            scheduledTaskMapper.releaseLock(lockName);
        } catch (Exception e) {
            log.warn("Failed to release scheduled task lock: {}", lockName, e);
        }
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
