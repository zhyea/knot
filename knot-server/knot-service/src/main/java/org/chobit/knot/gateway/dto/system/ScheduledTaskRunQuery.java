package org.chobit.knot.gateway.dto.system;

import org.chobit.knot.gateway.model.PageRequest;

public record ScheduledTaskRunQuery(
        Integer pageNum,
        Integer pageSize,
        String taskCode,
        /** 运行状态（ScheduledTaskRunStatusEnum）：1-运行中 2-成功 3-失败 */
        Integer status,
        String triggerType
) {
    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
