package org.chobit.knot.svc.dto.model;

import org.chobit.knot.common.model.PageRequest;

import java.util.List;

public record ExternalModelItemQuery(
        Integer pageNum,
        Integer pageSize,
        String sourceCode,
        /** 同步状态（ExternalModelSyncStatusEnum）：1-待同步 2-已同步 3-失败 */
        Integer syncStatus,
        String keyword,
        String modelType,
        List<Long> ids
) {
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
