package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class RoutingConsumerEntity {
    private Long id;
    private String consumerCode;
    private String name;
    private String userRealName;
    /** 归属用户登录名（业务码，绑定 username，非主键 id） */
    private String userUsername;
    private String secretKey;
    private Boolean returnUsageDetail;
    private Long rateLimitPolicyId;
    private Long quotaPolicyId;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
