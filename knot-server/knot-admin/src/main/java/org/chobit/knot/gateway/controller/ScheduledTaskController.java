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

    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    @PostMapping("/list")
    public PageResult<ScheduledTaskEntity> listTasks(@RequestBody(required = false) ScheduledTaskQuery query) {
        return scheduledTaskService.listTasks(query);
    }

    @PostMapping
    public ScheduledTaskEntity create(@RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.create(request);
    }

    @PutMapping("/{id}")
    public ScheduledTaskEntity update(@PathVariable Long id, @RequestBody ScheduledTaskRequest request) {
        return scheduledTaskService.update(id, request);
    }

    @PostMapping("/{id}/trigger")
    public void trigger(@PathVariable Long id) {
        scheduledTaskService.triggerNow(id);
    }

    @PostMapping("/runs")
    public PageResult<ScheduledTaskRunEntity> listRuns(@RequestBody(required = false) ScheduledTaskRunQuery query) {
        return scheduledTaskService.listRuns(query);
    }

    @PostMapping("/handlers")
    public List<String> handlers() {
        return scheduledTaskService.listHandlerCodes();
    }
}
