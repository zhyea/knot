package com.knot.gateway.vo.system;

public record OperationLogItem(Long id, String moduleCode, String actionCode, String targetId, String resultStatus) {
}
