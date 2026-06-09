package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class AdminMenuEntity {
    private Long id;
    private Long moduleId;
    private String moduleCode;
    private String moduleName;
    private String moduleIcon;
    private Integer moduleSortOrder;
    private Long parentId;
    private String menuCode;
    private String menuName;
    private String routePath;
    private String componentKey;
    private String icon;
    private Integer sortOrder;
    private String status;
}
