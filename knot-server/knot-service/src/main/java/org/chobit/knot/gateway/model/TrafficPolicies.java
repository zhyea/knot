package org.chobit.knot.gateway.model;

import java.util.List;

public record TrafficPolicies(RateLimitPolicy rateLimitPolicy,
                              List<QuotaPolicy> quotaPolicies) {

    /**
     * 避免调用方对空额度做 NPE：未配置时返回不可变空列表。
     */
    public List<QuotaPolicy> quotaPolicies() {
        return quotaPolicies == null ? List.of() : quotaPolicies;
    }
}
