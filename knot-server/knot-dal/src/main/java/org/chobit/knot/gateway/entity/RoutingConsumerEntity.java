package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingConsumerEntity {
    private Long id;
    private String consumerCode;
    private String name;
    private Long userId;
    private String userRealName;
    private String userUsername;
    private String secretKey;
    private Boolean returnUsageDetail;
    private Long rateLimitPolicyId;
    private Long quotaPolicyId;
    private String status;
}
