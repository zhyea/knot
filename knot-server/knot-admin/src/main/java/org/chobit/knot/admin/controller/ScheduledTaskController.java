package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.svc.dto.system.ScheduledTaskQuery;
import org.chobit.knot.svc.dto.system.ScheduledTaskRequest;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunQuery;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.admin.service.ScheduledTaskAdminService;
import org.chobit.knot.svc.dto.system.ScheduledTaskDto;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时任务管理面接口。
 * 返回 svc 的 record 而非 {@code dal.entity}，实体只允许出现在 {@code org.chobit.knot.svc.converter.ScheduledTaskConverter}。
 * 审计日志的 SpEL 用 record 的访问器形式（{@code #result.id()}），属性形式会静默解析成表达式原文。
 */
@RestController
@RequestMapping("/api/system/scheduled-tasks")
public class ScheduledTaskController {

    private final ScheduledTaskAdminService scheduledTaskService;

    public ScheduledTaskController(ScheduledTaskAdminService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    @PostMapping("/list")
    public PageResult<ScheduledTaskDto> listTasks(@RequestBody(required = false) ScheduledTaskQuery query) {
        return scheduledTaskService.listTasks(query);
    }

    @OperationLog(module = "scheduled-task", operation = "CREATE", entityType = "ScheduledTask",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.taskName()",
            description = "'新建定时任务'",
            newValueSpel = "#result")
    @PostMapping
    public ScheduledTaskDto create(@RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.create(request);
    }

    @OperationLog(module = "scheduled-task", operation = "UPDATE", entityType = "ScheduledTask",
            entityId = "#p0",
            entityNameAfter = "#result.taskName()",
            description = "'更新定时任务'",
            newValueSpel = "#result")
    @PutMapping("/{id}")
    public ScheduledTaskDto update(@PathVariable Long id, @RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.update(id, request);
    }

    @OperationLog(module = "scheduled-task", operation = "EXECUTE", entityType = "ScheduledTask",
            entityId = "#p0",
            description = "'手动触发定时任务'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/{id}/trigger")
    public void trigger(@PathVariable Long id) {
        scheduledTaskService.triggerNow(id);
    }

    @PostMapping("/runs")
    public PageResult<ScheduledTaskRunDto> listRuns(@RequestBody(required = false) ScheduledTaskRunQuery query) {
        return scheduledTaskService.listRuns(query);
    }

    @PostMapping("/handlers")
    public List<String> listHandlerCodes() {
        return scheduledTaskService.listHandlerCodes();
    }
}
