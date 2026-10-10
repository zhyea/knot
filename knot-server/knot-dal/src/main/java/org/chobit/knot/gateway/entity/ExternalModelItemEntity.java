package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExternalModelItemEntity {
    private Long id;
    private String sourceCode;
    private String modelId;
    private String canonicalSlug;
    private String modelName;
    private String providerName;
    private String modelUrl;
    private LocalDateTime modelCreatedAt;
    private String description;
    private Integer contextLength;
    private String inputModalitiesJson;
    private String outputModalitiesJson;
    private String rawJson;
    private String normalizedName;
    private String modelType;
    private String tagsJson;
    private Integer maxCompletionTokens;
    private Long logicalModelId;
    private Boolean ignored;
    /** 同步状态（ExternalModelSyncStatusEnum）：1-待同步 2-已同步 3-失败 */
    private Integer syncStatus;
    private String syncHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
