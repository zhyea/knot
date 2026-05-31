package org.chobit.knot.gateway.dto.system;

import org.chobit.knot.gateway.model.PageRequest;

public record ScheduledTaskQuery(
        Integer pageNum,
        Integer pageSize,
        String taskCode,
        String status,
        String handlerCode
) {
    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
