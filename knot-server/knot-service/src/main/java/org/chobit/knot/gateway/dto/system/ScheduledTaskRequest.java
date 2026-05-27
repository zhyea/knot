package org.chobit.knot.gateway.dto.system;

public record ScheduledTaskRequest(
        String taskCode,
        String taskName,
        String handlerCode,
        String cronExpression,
        String executionMode,
        String status,
        String description
) {
}
