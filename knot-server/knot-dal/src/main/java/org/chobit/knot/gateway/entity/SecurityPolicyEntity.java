package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class SecurityPolicyEntity {
    private Long id;
    private String policyCode;
    private String configJson;
    private String status;
}
