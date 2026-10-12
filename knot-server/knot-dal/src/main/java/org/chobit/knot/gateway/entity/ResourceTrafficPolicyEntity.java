package org.chobit.knot.gateway.entity;

import lombok.Data;

/**
 * 资源-策略绑定。绑定列一律存业务码（跨平台可移植），主键 id 仅由 join 派生用于展示/分组。
 */
@Data
public class ResourceTrafficPolicyEntity {
    private Long id;
    private String resourceType;
    /** 落库列：资源业务码（按 resource_type 指向对应表的 code），非主键 id */
    private String resourceCode;
    /** 由 join 资源表派生（r.id），不落库 */
    private Long resourceId;
    /** 落库列：频控策略业务码（kb_rate_limit_policies.policy_code），非主键 id */
    private String rateLimitPolicyCode;
}
