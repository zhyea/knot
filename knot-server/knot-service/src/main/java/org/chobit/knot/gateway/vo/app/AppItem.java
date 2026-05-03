package org.chobit.knot.gateway.vo.app;

import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;

public record AppItem(Long id, String appId, String name, Long owner, boolean enabled,
                      RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
}
