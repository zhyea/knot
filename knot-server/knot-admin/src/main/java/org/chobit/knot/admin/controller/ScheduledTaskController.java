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
 *
 * <p>⭐ 返回类型是 svc 的 record 而不是 {@code dal.entity}（2026-10-28 分层改造③）：
 * 实体只允许在 {@code org.chobit.knot.svc.converter.ScheduledTaskConverter} 里出现，
 * 于是「有没有实体漏出去」是一条 grep 就能出结果的事。</p>
 *
 * <p>⚠ <b>record 的 SpEL 要用访问器形式（{@code #result.id()}），不能用属性形式（{@code #result.id}）</b>：
 * record 组件是私有 final 字段 + 无 {@code getId()} 方法，属性形式的解析会失败，
 * 而切面对失败是<b>静默兜底</b>的（{@code parseSpelLong} 返回 null、{@code parseSpel} 返回表达式原文），
 * 于是审计日志里 entityId 变空、entityName 变成字面量 {@code "#result.taskName"}，不会抛错也不会告警。</p>
 */
@RestController
@RequestMapping("/api/system/scheduled-tasks")
public class ScheduledTaskController {

    private final ScheduledTaskAdminService scheduledTaskService;

    /**
     * Constructs a new instance.
     */
    public ScheduledTaskController(ScheduledTaskAdminService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<ScheduledTaskDto> listTasks(@RequestBody(required = false) ScheduledTaskQuery query) {
        return scheduledTaskService.listTasks(query);
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "scheduled-task", operation = "CREATE", entityType = "ScheduledTask",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.taskName()",
            description = "'新建定时任务'",
            newValueSpel = "#result")
    @PostMapping
    public ScheduledTaskDto create(@RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.create(request);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "scheduled-task", operation = "UPDATE", entityType = "ScheduledTask",
            entityId = "#p0",
            entityNameAfter = "#result.taskName()",
            description = "'更新定时任务'",
            newValueSpel = "#result")
    @PutMapping("/{id}")
    public ScheduledTaskDto update(@PathVariable Long id, @RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.update(id, request);
    }

    /**
     * Triggers the requested operation immediately. Executes the public operation.
     */
    @OperationLog(module = "scheduled-task", operation = "EXECUTE", entityType = "ScheduledTask",
            entityId = "#p0",
            description = "'手动触发定时任务'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/{id}/trigger")
    public void trigger(@PathVariable Long id) {
        scheduledTaskService.triggerNow(id);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/runs")
    public PageResult<ScheduledTaskRunDto> listRuns(@RequestBody(required = false) ScheduledTaskRunQuery query) {
        return scheduledTaskService.listRuns(query);
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @PostMapping("/handlers")
    public List<String> listHandlerCodes() {
        return scheduledTaskService.listHandlerCodes();
    }
}
