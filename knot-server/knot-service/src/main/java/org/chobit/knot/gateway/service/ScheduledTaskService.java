package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.EntityStatus;
import org.chobit.knot.gateway.dto.system.ScheduledTaskQuery;
import org.chobit.knot.gateway.dto.system.ScheduledTaskRequest;
import org.chobit.knot.gateway.dto.system.ScheduledTaskRunQuery;
import org.chobit.knot.gateway.entity.ScheduledTaskEntity;
import org.chobit.knot.gateway.entity.ScheduledTaskRunEntity;
import org.chobit.knot.gateway.mapper.ScheduledTaskMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.schedule.ScheduledTaskHandler;
import org.chobit.knot.gateway.service.schedule.ScheduledTaskSchedulerService;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScheduledTaskService {

    private static final Set<String> EXECUTION_MODES = Set.of("SINGLE", "BROADCAST");
    private static final Set<String> STATUSES = Set.of(EntityStatus.ENABLED, EntityStatus.DISABLED);

    private final ScheduledTaskMapper scheduledTaskMapper;
    private final ScheduledTaskSchedulerService schedulerService;
    private final Set<String> handlerCodes;

    public ScheduledTaskService(ScheduledTaskMapper scheduledTaskMapper,
                                ScheduledTaskSchedulerService schedulerService,
                                List<ScheduledTaskHandler> handlers) {
        this.scheduledTaskMapper = scheduledTaskMapper;
        this.schedulerService = schedulerService;
        this.handlerCodes = handlers.stream().map(ScheduledTaskHandler::handlerCode).collect(Collectors.toSet());
    }

    public PageResult<ScheduledTaskEntity> listTasks(ScheduledTaskQuery query) {
        PageRequest pageRequest = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ScheduledTaskEntity> pageInfo = new PageInfo<>(scheduledTaskMapper.listTasks(
                query != null ? query.taskCode() : null,
                query != null ? query.status() : null,
                query != null ? query.handlerCode() : null
        ));
        return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    public PageResult<ScheduledTaskRunEntity> listRuns(ScheduledTaskRunQuery query) {
        PageRequest pageRequest = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ScheduledTaskRunEntity> pageInfo = new PageInfo<>(scheduledTaskMapper.listRuns(
                query != null ? query.taskCode() : null,
                query != null ? query.status() : null,
                query != null ? query.triggerType() : null
        ));
        return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    @Transactional
    public ScheduledTaskEntity create(ScheduledTaskRequest request) {
        ScheduledTaskEntity entity = toEntity(new ScheduledTaskEntity(), request);
        if (scheduledTaskMapper.getTaskByCode(entity.getTaskCode()) != null) {
            throw new IllegalArgumentException("Task code already exists: " + entity.getTaskCode());
        }
        scheduledTaskMapper.insertTask(entity);
        ScheduledTaskEntity saved = scheduledTaskMapper.getTaskById(entity.getId());
        schedulerService.reschedule(saved);
        return saved;
    }

    @Transactional
    public ScheduledTaskEntity update(Long id, ScheduledTaskRequest request) {
        ScheduledTaskEntity existing = scheduledTaskMapper.getTaskById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Task not found: " + id);
        }
        ScheduledTaskEntity entity = toEntity(existing, request);
        entity.setId(id);
        scheduledTaskMapper.updateTask(entity);
        ScheduledTaskEntity saved = scheduledTaskMapper.getTaskById(id);
        schedulerService.reschedule(saved);
        return saved;
    }

    public void triggerNow(Long id) {
        ScheduledTaskEntity task = scheduledTaskMapper.getTaskById(id);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + id);
        }
        schedulerService.triggerNow(task.getTaskCode());
    }

    public List<String> listHandlerCodes() {
        return handlerCodes.stream().sorted().toList();
    }

    private ScheduledTaskEntity toEntity(ScheduledTaskEntity entity, ScheduledTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        entity.setTaskCode(required(request.taskCode(), "taskCode"));
        entity.setTaskName(required(request.taskName(), "taskName"));
        entity.setHandlerCode(required(request.handlerCode(), "handlerCode"));
        entity.setCronExpression(required(request.cronExpression(), "cronExpression"));
        entity.setExecutionMode(required(request.executionMode(), "executionMode"));
        entity.setStatus(required(request.status(), "status"));
        entity.setDescription(blankToNull(request.description()));

        if (!handlerCodes.contains(entity.getHandlerCode())) {
            throw new IllegalArgumentException("Unsupported handlerCode: " + entity.getHandlerCode());
        }
        if (!EXECUTION_MODES.contains(entity.getExecutionMode())) {
            throw new IllegalArgumentException("Unsupported executionMode: " + entity.getExecutionMode());
        }
        if (!STATUSES.contains(entity.getStatus())) {
            throw new IllegalArgumentException("Unsupported status: " + entity.getStatus());
        }
        if (!CronExpression.isValidExpression(entity.getCronExpression())) {
            throw new IllegalArgumentException("Invalid cron expression: " + entity.getCronExpression());
        }
        return entity;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
