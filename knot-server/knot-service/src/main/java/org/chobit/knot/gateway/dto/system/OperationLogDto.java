package org.chobit.knot.gateway.dto.system;

public record OperationLogDto(Long id, String moduleCode, String actionCode, String targetId, String resultStatus) {
}
