package org.chobit.knot.gateway.dto.system;

import org.chobit.knot.gateway.model.PageRequest;

public record ScheduledTaskRunQuery(
        Integer pageNum,
        Integer pageSize,
        String taskCode,
        String status,
        String triggerType
) {
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
