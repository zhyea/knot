package org.chobit.knot.admin.controller;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.admin.service.AsyncTaskAdminService;
import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.dto.async.AsyncTaskOperationResponse;
import org.chobit.knot.svc.dto.async.AsyncTaskQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异步任务管理面（{@code kb_async_tasks} 的查看与运维动作）。
 * 本类只绑定入参、委托给 {@link AsyncTaskAdminService}，HTTP 状态码由异常体系决定；分页、校验、透传网关都在 Service 里。
 * 读走库（{@code list} / 详情直接查库），写走网关（{@code cancel} / {@code retry} / {@code cleanup} 透传给 {@code /_admin/async-tasks*}）。
 * 返回 svc 的 record 而非 {@code dal.entity}，实体只允许出现在 {@code svc.converter}。
 */
@RestController
@RequestMapping("/api/routing/async-tasks")
@RequiredArgsConstructor
public class AsyncTaskController {

    private final AsyncTaskAdminService asyncTaskService;

    /** 分页检索（筛选条件见 {@link AsyncTaskQuery}） */
    @PostMapping("/list")
    public PageResult<AsyncTaskDto> list(@RequestBody(required = false) AsyncTaskQuery query) {
        return asyncTaskService.list(query);
    }

    /** 任务详情：含四段 JSON 原文（请求体 / 查询口径快照 / 计费上下文 / 终态产物） */
    @GetMapping("/{taskCode}")
    public AsyncTaskDto detail(@PathVariable("taskCode") String taskCode) {
        return asyncTaskService.detail(taskCode);
    }

    /**
     * 终止一条在途任务。审计日志的 SpEL 用 record 的访问器形式（{@code #result.taskCode()}）。
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "AsyncTask",
            entityName = "'异步任务 ' + #p0",
            description = "'终止异步任务'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/{taskCode}/cancel")
    public AsyncTaskOperationResponse cancel(@PathVariable("taskCode") String taskCode) {
        return asyncTaskService.cancel(taskCode);
    }

    /** 重试一条失败 / 超时的任务（重开轮询，不改上游任务号） */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "AsyncTask",
            entityName = "'异步任务 ' + #p0",
            description = "'重试异步任务'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/{taskCode}/retry")
    public AsyncTaskOperationResponse retry(@PathVariable("taskCode") String taskCode) {
        return asyncTaskService.retry(taskCode);
    }

    /**
     * 立即执行一次「按保留窗口清理终态行」，返回删除行数；不接受自定义时间窗，保留期见配置 {@code knot.gateway.async-task.retention-days}。
     */
    @OperationLog(module = "routing", operation = "DELETE", entityType = "AsyncTask",
            entityName = "'清理终态异步任务'",
            description = "'清理终态异步任务'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/cleanup")
    public int cleanup() {
        return asyncTaskService.cleanup();
    }
}
