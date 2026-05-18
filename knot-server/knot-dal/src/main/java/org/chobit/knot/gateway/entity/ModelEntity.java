package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelEntity {
    private Long id;
    private Long providerId;
    /** 关联 providers.name，仅查询展示 */
    private String providerName;
    private String modelCode;
    private String name;
    private String modelType;
    private String version;
    private String status;
}
