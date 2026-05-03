package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelEntity {
    private Long id;
    private Long providerId;
    private String modelCode;
    private String name;
    private String modelType;
    private String version;
    private String status;
    private String rateLimitJson;
    private String quotaJson;
}
