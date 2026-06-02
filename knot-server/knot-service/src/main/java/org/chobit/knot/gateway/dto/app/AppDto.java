package org.chobit.knot.gateway.dto.app;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record AppDto(
        Long id,
        String appId,
        String name,
        Long deptId,
        String deptName,
        Long ownerUserId,
        String ownerName,
        String remark,
        RateLimitPolicy rateLimitPolicy,
        QuotaPolicy quotaPolicy
) {
}
