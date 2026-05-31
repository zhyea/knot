package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.dto.system.ScheduledTaskQuery;
import org.chobit.knot.gateway.dto.system.ScheduledTaskRequest;
import org.chobit.knot.gateway.dto.system.ScheduledTaskRunQuery;
import org.chobit.knot.gateway.entity.ScheduledTaskEntity;
import org.chobit.knot.gateway.entity.ScheduledTaskRunEntity;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.ScheduledTaskService;
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
    @PostMapping
    public ScheduledTaskEntity create(@RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.create(request);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @PutMapping("/{id}")
    public ScheduledTaskEntity update(@PathVariable Long id, @RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.update(id, request);
    }

    /**
     * Triggers the requested operation immediately. Executes the public operation.
     */
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
