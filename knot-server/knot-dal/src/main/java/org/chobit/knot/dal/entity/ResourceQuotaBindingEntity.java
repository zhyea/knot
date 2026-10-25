package org.chobit.knot.dal.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源-额度策略绑定子表：一个资源可挂多套不同统计窗口的额度策略
 * （每窗口一份 token + 成本上限），通过 {@code quota_policy_code} 指向 {@code kb_quota_policies}。
 *
 * <p>绑定列一律存业务码（resource_code / quota_policy_code），主键 id 仅用于唯一约束与展示。</p>
 */
@Data
public class ResourceQuotaBindingEntity {
    private Long id;
    private String resourceType;
    private String resourceCode;
    private String quotaPolicyCode;
    private LocalDateTime createdAt;
}
