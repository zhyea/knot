package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleConsumerEntity {
    private Long id;
    private Long ruleId;
    private Long consumerId;
    private String consumerCode;
    private String consumerName;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
