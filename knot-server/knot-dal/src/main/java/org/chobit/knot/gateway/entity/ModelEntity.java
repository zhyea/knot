package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelEntity {
    private Long id;
    private Long providerId;
    /** 关联 kb_providers.name，仅查询展示 */
    private String providerName;
    private String modelCode;
    /** 派生字段：取自绑定的统一模型（kb_provider_model_mappings → kb_logical_models），不落 kb_models */
    private String name;
    /** 派生字段：取自绑定的统一模型，不落 kb_models */
    private String modelType;
    private String version;
    private String baseUrl;
    private String remark;
    private Long billingRuleId;
    private String billingRuleName;
    private String status;
}
