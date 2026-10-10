package org.chobit.knot.gateway.vo.auth;

import java.util.List;

public record AdminAuthorizationInfoResponse(
        Long userId,
        String username,
        String realName,
        /** 归属部门业务码（ks_departments.dept_code，非主键 id） */
        String deptCode,
        String deptName,
        List<String> roles,
        List<String> permissions,
        List<AdminModuleItem> modules
) {
}
