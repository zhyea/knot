package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelVersionEntity {
    private Long id;
    private Long modelId;
    private String version;
    private Integer grayPercent;
    private String status;
}
