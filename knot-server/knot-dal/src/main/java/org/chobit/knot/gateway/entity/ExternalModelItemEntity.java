package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExternalModelItemEntity {
    private Long id;
    private String sourceCode;
    private String sourceModelId;
    private String sourceModelName;
    private String sourceProviderCode;
    private String sourceProviderName;
    private String sourceUrl;
    private String sourceLlmType;
    private String sourceLlmTypeCode;
    private String sourceTagType;
    private String sourceContextLength;
    private String sourceCapabilitiesJson;
    private LocalDateTime sourceLastUpdateTime;
    private String sourceDescription;
    private String rawJson;
    private String normalizedName;
    private String normalizedFamily;
    private String modelType;
    private String tagsJson;
    private String capabilitiesJson;
    private Integer contextWindow;
    private Integer maxOutputTokens;
    private String languagesJson;
    private Long logicalModelId;
    private String syncStatus;
    private String syncHash;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
