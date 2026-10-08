package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LogicalModelEntity {
    private Long id;
    private String modelCode;
    private String modelName;
    private String modelType;
    private String modelFamily;
    private String displayName;
    private String description;
    private String tagsJson;
    private String useCasesJson;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private String inputModalitiesJson;
    private String outputModalitiesJson;
    private String languagesJson;
    private String visibility;
    /** 发布状态（LogicalModelPublishStatusEnum）：1-DRAFT 2-PUBLISHED 3-ARCHIVED */
    private Integer publishStatus;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private Integer isDeleted;
    private Integer sortOrder;
    private Boolean featured;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
