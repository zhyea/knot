package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ResourceTrafficPolicyEntity {
    private Long id;
    private String resourceType;
    private Long resourceId;
    private Long rateLimitPolicyId;
    private Long quotaPolicyId;
}
