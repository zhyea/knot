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
    private String version;
    private String displayName;
    private String tagline;
    private String description;
    private String logoUrl;
    private String coverUrl;
    private String tagsJson;
    private String useCasesJson;
    private String capabilitiesJson;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private String inputModalitiesJson;
    private String outputModalitiesJson;
    private String languagesJson;
    private String defaultParamsJson;
    private String paramSchemaJson;
    private String safetyPolicyJson;
    private String visibility;
    private String publishStatus;
    private String status;
    private Integer sortOrder;
    private Boolean featured;
    private Long ownerUserId;
    private String ownerTeam;
    private String qualityLevel;
    private String latencyLevel;
    private String costLevel;
    private String pricingSummary;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
