package org.chobit.knot.gateway.service.schedule;

import org.chobit.knot.gateway.mapper.ScheduledTaskMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ScheduleRunRetentionHandler implements ScheduledTaskHandler {

    public static final String CODE = "SCHEDULE_RUN_RETENTION";

    private final ScheduledTaskMapper scheduledTaskMapper;

    public ScheduleRunRetentionHandler(ScheduledTaskMapper scheduledTaskMapper) {
        this.scheduledTaskMapper = scheduledTaskMapper;
    }

    @Override
    public String handlerCode() {
        return CODE;
    }

    @Override
    public ScheduledTaskResult execute() {
        LocalDateTime beforeTime = LocalDateTime.now().minusMonths(1);
        int rows = scheduledTaskMapper.deleteRunsBefore(beforeTime);
        return new ScheduledTaskResult(rows, "Deleted scheduled task runs before " + beforeTime);
    }
}
