package org.chobit.knot.gateway.service.schedule;

import org.chobit.knot.gateway.service.OperationLogService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OperationLogRetentionHandler implements ScheduledTaskHandler {

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static final String CODE = "OPERATION_LOG_RETENTION";

    private final OperationLogService operationLogService;

    /**
     * Constructs a new instance.
     */
    public OperationLogRetentionHandler(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
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
        LocalDateTime beforeTime = LocalDateTime.now().minusMonths(3);
        int rows = operationLogService.deleteBefore(beforeTime);
        return new ScheduledTaskResult(rows, "Deleted operation logs before " + beforeTime);
    }
}
