package org.chobit.knot.dal.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderModelMappingEntity {
    private Long id;
    private String logicalModelCode;
    private String logicalModelName;
    private String providerAccountCode;
    private String providerName;
    /** 由 join kb_models 派生（m.id），不落库 */
    private Long modelId;
    /** 落库列：供应商模型业务码（kb_models.model_code），非主键 id */
    private String modelCode;
    private String modelName;
    private String providerModelName;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
