package org.chobit.knot.admin.service.schedule;

import org.chobit.knot.svc.service.ScheduledTaskService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 清理过期的任务执行记录。
 *
 * <p>⚠ <b>本类是「handler 自己也要遵守分层」的判例</b>：改造前它直接注入
 * {@code ScheduledTaskMapper} 调 {@code deleteRunsBefore}——同一个模块里
 * 另外三个类也在注入同一个 Mapper，四个入口各写各的。
 * handler 不是「业务逻辑的例外」，它照样只能通过 svc 的 {@link ScheduledTaskService} 用库。</p>
 */
@Component
public class ScheduleRunRetentionHandler implements ScheduledTaskHandler {

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static final String CODE = "SCHEDULE_RUN_RETENTION";

    private final ScheduledTaskService taskService;

    /**
     * Constructs a new instance.
     */
    public ScheduleRunRetentionHandler(ScheduledTaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @Override
    public String handlerCode() {
        return CODE;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public ScheduledTaskResult execute() {
        LocalDateTime beforeTime = LocalDateTime.now().minusMonths(1);
        int rows = taskService.deleteRunsBefore(beforeTime);
        return new ScheduledTaskResult(rows, "Deleted scheduled task runs before " + beforeTime);
    }
}
