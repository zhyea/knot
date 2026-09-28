package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelPoolItemEntity {
    private Long id;
    private String poolCode;
    private String modelCode;
    private Integer weight;
    private Integer priority;
    private String status;
    private String modelName;
    private String modelType;
    private String providerAccountCode;
    private String providerName;
}
