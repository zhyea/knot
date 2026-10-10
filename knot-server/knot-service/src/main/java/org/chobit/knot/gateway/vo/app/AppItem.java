package org.chobit.knot.gateway.vo.app;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record AppItem(
        Long id,
        String appCode,
        String name,
        /** 归属部门业务码（ks_departments.dept_code，非主键 id） */
        String deptCode,
        String deptName,
        String ownerUsername,
        String ownerName,
        String remark,
        RateLimitPolicy rateLimitPolicy,
        QuotaPolicy quotaPolicy
) {
}
