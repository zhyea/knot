package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExternalModelItemEntity {
    private Long id;
    private String sourceCode;
    private String modelId;
    private String canonicalSlug;
    private String huggingFaceId;
    private String modelName;
    private String providerCode;
    private String providerName;
    private String modelUrl;
    private String detailsPath;
    private Long createdTimestamp;
    private LocalDateTime modelCreatedAt;
    private String description;
    private Integer contextLength;
    private String modality;
    private String inputModalitiesJson;
    private String outputModalitiesJson;
    private String tokenizer;
    private String instructType;
    private String pricingJson;
    private String topProviderJson;
    private String supportedParametersJson;
    private String defaultParametersJson;
    private String supportedVoicesJson;
    private String knowledgeCutoff;
    private String expirationDate;
    private String rawJson;
    private String normalizedName;
    private String modelFamily;
    private String modelType;
    private String tagsJson;
    private String capabilitiesJson;
    private Integer maxCompletionTokens;
    private Long logicalModelId;
    private String syncStatus;
    private String syncHash;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
