package org.chobit.knot.admin.service.schedule;

public record ScheduledTaskResult(
        int affectedRows,
        String message
) {
}
