package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelPoolItemEntity {
    private Long id;
    private Long poolId;
    private Long modelId;
    private Integer weight;
    private Integer priority;
    private String status;
    private String modelCode;
    private String modelName;
    private String modelType;
    private Long providerId;
    private String providerName;
}
