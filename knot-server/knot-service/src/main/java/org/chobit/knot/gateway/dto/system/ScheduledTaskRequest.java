package org.chobit.knot.gateway.dto.system;

public record ScheduledTaskRequest(
        String taskCode,
        String taskName,
        String handlerCode,
        String cronExpression,
        String executionMode,
        /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
        Integer status,
        String description
) {
}
