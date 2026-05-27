package org.chobit.knot.gateway.service.schedule;

import org.chobit.knot.gateway.service.OperationLogService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OperationLogRetentionHandler implements ScheduledTaskHandler {

    public static final String CODE = "OPERATION_LOG_RETENTION";

    private final OperationLogService operationLogService;

    public OperationLogRetentionHandler(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Override
    public String handlerCode() {
        return CODE;
    }

    @Override
    public ScheduledTaskResult execute() {
        LocalDateTime beforeTime = LocalDateTime.now().minusMonths(3);
        int rows = operationLogService.deleteBefore(beforeTime);
        return new ScheduledTaskResult(rows, "Deleted operation logs before " + beforeTime);
    }
}
