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
    /** 池内粘性路由策略（JSON）；空表示未启用，池选择走 selection_strategy */
    private String stickyPolicy;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private Integer isDeleted;
    private String remark;
}
