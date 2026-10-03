package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelPoolEntity {
    private Long id;
    private String poolCode;
    private String name;
    /** 绑定统一模型 code（kb_logical_models.model_code），池内模型必须全部归属该统一模型 */
    private String logicalModelCode;
    /** 派生字段：统一模型名称，仅查询展示 */
    private String logicalModelName;
    /** 派生字段：取自绑定统一模型的 model_type，不落 kb_model_pools */
    private String modelType;
    private String selectionStrategy;
    private String status;
    private Integer isDeleted;
    private String remark;
}
