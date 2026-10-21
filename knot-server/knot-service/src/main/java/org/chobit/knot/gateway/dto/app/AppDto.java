package org.chobit.knot.gateway.dto.app;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

import java.util.List;

public record AppDto(
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
        List<QuotaPolicy> quotaPolicies,
        /** 只读派生：应用编码被路由规则引用的处数；0 表示未绑定，可改应用编码 */
        Long boundCount
) {
}
