package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelEntity {
    private Long id;
    /** 所属供应商账户 code（kb_provider_accounts.code） */
    private String providerAccountCode;
    /** 关联 kb_providers.name，仅查询展示 */
    private String providerName;
    /** 关联 kb_providers.code（经账户推导），仅查询展示 */
    private String providerCode;
    private String modelCode;
    /** 上游模型标识：向上游发起请求时写入请求体 model 参数 */
    private String upstreamModel;
    /** 派生字段：取自 kb_provider_model_mappings（供应商模型 → 统一模型 1:1 映射） */
    private String logicalModelCode;
    /** 派生字段：取自绑定的统一模型（kb_provider_model_mappings → kb_logical_models），不落 kb_models */
    private String name;
    /** 派生字段：取自绑定的统一模型，不落 kb_models */
    private String modelType;
    /** 派生字段：取自绑定的统一模型（kb_logical_models.model_family），供计费规则按族回退匹配 */
    private String modelFamilyCode;
    private String version;
    private String baseUrl;
    private String remark;
    /** 绑定计费规则业务码（kb_billing_rules.code），非主键 id */
    private String billingRuleCode;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    /** 逻辑删除：0-否 1-是（uk_models_code 不区分 is_deleted，删除后同 code 只能恢复，不能新建） */
    private Integer isDeleted;
}
