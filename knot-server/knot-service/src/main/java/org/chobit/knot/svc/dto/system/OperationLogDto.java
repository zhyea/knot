package org.chobit.knot.svc.dto.system;

public record OperationLogDto(Long id, String moduleCode, String actionCode, String targetId, String resultStatus) {
}
