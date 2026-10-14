package org.chobit.knot.gateway.service.schedule;

import org.chobit.knot.gateway.mapper.ScheduledTaskMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ScheduleRunRetentionHandler implements ScheduledTaskHandler {

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static final String CODE = "SCHEDULE_RUN_RETENTION";

    private final ScheduledTaskMapper scheduledTaskMapper;

    /**
     * Constructs a new instance.
     */
    public ScheduleRunRetentionHandler(ScheduledTaskMapper scheduledTaskMapper) {
        this.scheduledTaskMapper = scheduledTaskMapper;
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
        int rows = scheduledTaskMapper.deleteRunsBefore(beforeTime);
        return new ScheduledTaskResult(rows, "Deleted scheduled task runs before " + beforeTime);
    }
}
