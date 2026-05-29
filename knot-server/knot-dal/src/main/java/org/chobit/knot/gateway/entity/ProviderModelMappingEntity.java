package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProviderModelMappingEntity {
    private Long id;
    private Long logicalModelId;
    private String logicalModelCode;
    private String logicalModelName;
    private Long providerId;
    private String providerName;
    private Long modelId;
    private String modelCode;
    private String modelName;
    private String providerModelName;
    private String status;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
