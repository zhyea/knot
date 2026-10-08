package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AdminModuleEntity {
    private Long id;
    private String moduleCode;
    private String moduleName;
    private String icon;
    private Integer sortOrder;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
}
