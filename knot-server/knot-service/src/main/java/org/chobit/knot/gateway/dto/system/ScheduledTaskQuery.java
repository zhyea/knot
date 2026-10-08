package org.chobit.knot.gateway.dto.system;

import org.chobit.knot.gateway.model.PageRequest;

public record ScheduledTaskQuery(
        Integer pageNum,
        Integer pageSize,
        String keyword,
        /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
        Integer status,
        String handlerCode
) {
    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
