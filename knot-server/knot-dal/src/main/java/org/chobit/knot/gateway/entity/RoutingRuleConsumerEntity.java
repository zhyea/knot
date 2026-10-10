package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingRuleConsumerEntity {
    private Long id;
    /** 路由规则业务码（kb_routing_rules.rule_code），非主键 id */
    private String ruleCode;
    /** 消费者业务码（kb_routing_consumers.consumer_code），非主键 id，落库列 */
    private String consumerCode;
    /** 消费者主键 id：由 join kb_routing_consumers 派生，仅供读接口回传前端（前端选项以 id 为值），不落库 */
    private Long consumerId;
    private String consumerName;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
