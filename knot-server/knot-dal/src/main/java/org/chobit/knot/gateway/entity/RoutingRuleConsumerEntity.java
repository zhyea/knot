package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleConsumerEntity {
    private Long id;
    private Long ruleId;
    private Long consumerId;
    private String consumerCode;
    private String consumerName;
    private String status;
}
