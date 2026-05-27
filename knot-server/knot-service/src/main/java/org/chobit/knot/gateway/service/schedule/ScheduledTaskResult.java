package org.chobit.knot.gateway.service.schedule;

public record ScheduledTaskResult(
        int affectedRows,
        String message
) {
}
