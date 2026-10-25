package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.svc.dto.system.ScheduledTaskQuery;
import org.chobit.knot.svc.dto.system.ScheduledTaskRequest;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunQuery;
import org.chobit.knot.dal.entity.ScheduledTaskEntity;
import org.chobit.knot.dal.entity.ScheduledTaskRunEntity;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.admin.service.ScheduledTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/scheduled-tasks")
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    /**
     * Constructs a new instance.
     */
    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<ScheduledTaskEntity> listTasks(@RequestBody(required = false) ScheduledTaskQuery query) {
        return scheduledTaskService.listTasks(query);
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "scheduled-task", operation = "CREATE", entityType = "ScheduledTask",
            entityIdAfter = "#result.id",
            entityNameAfter = "#result.taskName",
            description = "'新建定时任务'",
            newValueSpel = "#result")
    @PostMapping
    public ScheduledTaskEntity create(@RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.create(request);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "scheduled-task", operation = "UPDATE", entityType = "ScheduledTask",
            entityId = "#p0",
            entityNameAfter = "#result.taskName",
            description = "'更新定时任务'",
            newValueSpel = "#result")
    @PutMapping("/{id}")
    public ScheduledTaskEntity update(@PathVariable Long id, @RequestBody ScheduledTaskRequest request) {
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
    public PageResult<ScheduledTaskRunEntity> listRuns(@RequestBody(required = false) ScheduledTaskRunQuery query) {
        return scheduledTaskService.listRuns(query);
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @PostMapping("/handlers")
    public List<String> handlers() {
        return scheduledTaskService.listHandlerCodes();
    }
}
