package org.chobit.knot.gateway.vo.auth;

import java.util.List;

public record AdminAuthorizationInfoResponse(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName,
        List<String> roles,
        List<String> permissions,
        List<AdminModuleItem> modules
) {
}
