package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderModelMappingEntity {
    private Long id;
    private String logicalModelCode;
    private String logicalModelName;
    private String providerAccountCode;
    private String providerName;
    private Long modelId;
    private String modelCode;
    private String modelName;
    private String providerModelName;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
