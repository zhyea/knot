package org.chobit.knot.svc.dto.system;

import org.chobit.knot.common.model.PageRequest;

public record ScheduledTaskQuery(
        Integer pageNum,
        Integer pageSize,
        String keyword,
        /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
        Integer status,
        String handlerCode
) {
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
