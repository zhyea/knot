package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelPoolEntity {
    private Long id;
    private String poolCode;
    private String name;
    private String modelType;
    private String selectionStrategy;
    private String status;
    private String remark;
}
