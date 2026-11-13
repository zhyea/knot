package org.chobit.knot.admin.service.schedule;

import org.chobit.knot.svc.service.ScheduledTaskService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 清理过期的任务执行记录。
 * 只通过 svc 的 {@link ScheduledTaskService} 访问数据库，不直接注入 Mapper。
 */
@Component
public class ScheduleRunRetentionHandler implements ScheduledTaskHandler {

    public static final String CODE = "SCHEDULE_RUN_RETENTION";

    private final ScheduledTaskService taskService;

    public ScheduleRunRetentionHandler(ScheduledTaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String handlerCode() {
        return CODE;
    }

    @Override
    public ScheduledTaskResult execute() {
        LocalDateTime beforeTime = LocalDateTime.now().minusMonths(1);
        int rows = taskService.deleteRunsBefore(beforeTime);
        return new ScheduledTaskResult(rows, "Deleted scheduled task runs before " + beforeTime);
    }
}
