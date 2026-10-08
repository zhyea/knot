package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelPoolItemEntity {
    private Long id;
    private String poolCode;
    private String modelCode;
    private Integer weight;
    private Integer priority;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private String modelName;
    private String modelType;
    private String providerAccountCode;
    private String providerName;
}
