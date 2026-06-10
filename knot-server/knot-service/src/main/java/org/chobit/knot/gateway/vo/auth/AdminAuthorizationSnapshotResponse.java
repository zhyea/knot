package org.chobit.knot.gateway.vo.auth;

import java.util.List;

public record AdminAuthorizationSnapshotResponse(
        List<AdminRoleSummary> roles,
        List<Long> grantedPermissionIds
) {
    public record AdminRoleSummary(Long id, String code, String name) {
    }
}
