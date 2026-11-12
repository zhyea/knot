package org.chobit.knot.svc.dto.system;

import org.chobit.knot.common.model.PageRequest;

public record ScheduledTaskRunQuery(
        Integer pageNum,
        Integer pageSize,
        String taskCode,
        /** 运行状态（ScheduledTaskRunStatusEnum）：1-运行中 2-成功 3-失败 */
        Integer status,
        String triggerType
) {
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
