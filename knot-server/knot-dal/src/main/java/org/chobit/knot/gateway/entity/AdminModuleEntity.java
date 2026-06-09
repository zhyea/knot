package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AdminModuleEntity {
    private Long id;
    private String moduleCode;
    private String moduleName;
    private String icon;
    private Integer sortOrder;
    private String status;
}
