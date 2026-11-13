package org.chobit.knot.admin.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.admin.service.schedule.ScheduledTaskHandler;
import org.chobit.knot.admin.service.schedule.ScheduledTaskSchedulerService;
import org.chobit.knot.svc.dto.system.ScheduledTaskQuery;
import org.chobit.knot.svc.dto.system.ScheduledTaskRequest;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunQuery;
import org.chobit.knot.svc.dto.system.ScheduledTaskDto;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunDto;
import org.chobit.knot.svc.service.ScheduledTaskService;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 定时任务的管理面编排：参数校验 + 写库 + 重新排期。
 * 数据读写全部走 svc 的 {@link ScheduledTaskService}；调度基础设施（Quartz）属管理端专属，故本类留在 admin。
 * 类名带 {@code Admin} 以区别于 svc 侧同名的 {@code ScheduledTaskService}。
 */
@Service
public class ScheduledTaskAdminService {

    private static final Set<String> EXECUTION_MODES = Set.of("SINGLE", "BROADCAST");
    private static final Set<Integer> STATUSES = Set.of(EnabledStatusEnum.ENABLED.code(), EnabledStatusEnum.DISABLED.code());

    private final ScheduledTaskService taskService;
    private final ScheduledTaskSchedulerService schedulerService;
    private final Set<String> handlerCodes;

    public ScheduledTaskAdminService(ScheduledTaskService taskService,
                                    ScheduledTaskSchedulerService schedulerService,
                                    List<ScheduledTaskHandler> handlers) {
        this.taskService = taskService;
        this.schedulerService = schedulerService;
        this.handlerCodes = handlers.stream().map(ScheduledTaskHandler::handlerCode).collect(Collectors.toSet());
    }

    public PageResult<ScheduledTaskDto> listTasks(ScheduledTaskQuery query) {
        PageRequest pageRequest = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ScheduledTaskDto> pageInfo = new PageInfo<>(taskService.listTasks(
                    query != null ? StringUtils.trimToNull(query.keyword()) : null,
                    query != null ? query.status() : null,
                    query != null ? query.handlerCode() : null
            ));
            return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    public PageResult<ScheduledTaskRunDto> listRuns(ScheduledTaskRunQuery query) {
        PageRequest pageRequest = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ScheduledTaskRunDto> pageInfo = new PageInfo<>(taskService.listRuns(
                    query != null ? query.taskCode() : null,
                    query != null ? query.status() : null,
                    query != null ? query.triggerType() : null
            ));
            return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    @Transactional
    public ScheduledTaskDto create(ScheduledTaskRequest request) {
        ScheduledTaskDto draft = validated(null, request);
        if (taskService.getTaskByCode(draft.taskCode()) != null) {
            throw new IllegalArgumentException("Task code already exists: " + draft.taskCode());
        }
        Long id = taskService.insertTask(draft);
        ScheduledTaskDto saved = taskService.getTaskById(id);
        schedulerService.reschedule(saved);
        return saved;
    }

    @Transactional
    public ScheduledTaskDto update(Long id, ScheduledTaskRequest request) {
        ScheduledTaskDto existing = taskService.getTaskById(id);
        if (existing == null) {
            throw new IllegalArgumentException("Task not found: " + id);
        }
        ScheduledTaskDto updated = validated(existing, request);
        taskService.updateTask(updated);
        ScheduledTaskDto saved = taskService.getTaskById(id);
        schedulerService.reschedule(saved);
        return saved;
    }

    public void triggerNow(Long id) {
        ScheduledTaskDto task = taskService.getTaskById(id);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + id);
        }
        schedulerService.triggerNow(task.taskCode());
    }

    public List<String> listHandlerCodes() {
        return handlerCodes.stream().sorted().toList();
    }

    /**
     * 校验并投影成一条完整记录；{@code existing} 为 {@code null} 表示新建。
     * 更新时必须带上 {@code id} 与创建 / 更新时间这些不由请求体决定的字段，否则 {@code updateTask} 会把它们覆盖成 {@code null}。
     */
    private ScheduledTaskDto validated(ScheduledTaskDto existing, ScheduledTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String handlerCode = required(request.handlerCode(), "handlerCode");
        String executionMode = required(request.executionMode(), "executionMode");
        String cronExpression = required(request.cronExpression(), "cronExpression");
        if (request.status() == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (!handlerCodes.contains(handlerCode)) {
            throw new IllegalArgumentException("Unsupported handlerCode: " + handlerCode);
        }
        if (!EXECUTION_MODES.contains(executionMode)) {
            throw new IllegalArgumentException("Unsupported executionMode: " + executionMode);
        }
        if (!STATUSES.contains(request.status())) {
            throw new IllegalArgumentException("Unsupported status: " + request.status());
        }
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression);
        }
        return new ScheduledTaskDto(
                existing == null ? null : existing.id(),
                required(request.taskCode(), "taskCode"),
                required(request.taskName(), "taskName"),
                handlerCode,
                cronExpression,
                executionMode,
                request.status(),
                blankToNull(request.description()),
                existing == null ? null : existing.lastFireAt(),
                existing == null ? null : existing.nextFireAt(),
                existing == null ? null : existing.createdAt(),
                existing == null ? null : existing.updatedAt());
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
