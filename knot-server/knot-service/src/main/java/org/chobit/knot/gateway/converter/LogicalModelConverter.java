package org.chobit.knot.gateway.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.dto.model.LogicalModelDto;
import org.chobit.knot.gateway.dto.model.ProviderModelMappingDto;
import org.chobit.knot.gateway.entity.LogicalModelEntity;
import org.chobit.knot.gateway.entity.ProviderModelMappingEntity;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.model.LogicalModelItem;
import org.chobit.knot.gateway.vo.model.ProviderModelMappingItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LogicalModelConverter {

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public LogicalModelDto toDto(LogicalModelEntity entity) {
        if (entity == null) {
            return null;
        }
        return new LogicalModelDto(
                entity.getId(),
                entity.getModelCode(),
                entity.getModelName(),
                entity.getModelType(),
                entity.getModelFamily(),
                entity.getVersion(),
                entity.getExternalSourceCode(),
                entity.getExternalModelId(),
                entity.getCanonicalSlug(),
                entity.getProviderCode(),
                entity.getProviderName(),
                entity.getDisplayName(),
                entity.getTagline(),
                entity.getDescription(),
                entity.getLogoUrl(),
                entity.getCoverUrl(),
                toStringList(entity.getTagsJson()),
                toStringList(entity.getUseCasesJson()),
                toMap(entity.getCapabilitiesJson()),
                entity.getContextWindow(),
                entity.getMaxOutputTokens(),
                toStringList(entity.getInputModalitiesJson()),
                toStringList(entity.getOutputModalitiesJson()),
                toStringList(entity.getLanguagesJson()),
                toMap(entity.getDefaultParamsJson()),
                toMap(entity.getParamSchemaJson()),
                toMap(entity.getSafetyPolicyJson()),
                toMap(entity.getPricingJson()),
                toStringList(entity.getSupportedParametersJson()),
                entity.getVisibility(),
                entity.getPublishStatus(),
                EntityStatusEnum.ENABLED.code().equals(entity.getStatus()),
                entity.getSortOrder(),
                Boolean.TRUE.equals(entity.getFeatured()),
                entity.getOwnerUserId(),
                entity.getOwnerTeam(),
                entity.getQualityLevel(),
                entity.getLatencyLevel(),
                entity.getCostLevel(),
                entity.getPricingSummary(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                List.of()
        );
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public LogicalModelEntity toEntity(LogicalModelDto dto) {
        LogicalModelEntity entity = new LogicalModelEntity();
        entity.setId(dto.id());
        entity.setModelCode(dto.modelCode());
        entity.setModelName(dto.modelName());
        entity.setModelType(dto.modelType());
        entity.setModelFamily(dto.modelFamily());
        entity.setVersion(dto.version());
        entity.setExternalSourceCode(dto.externalSourceCode());
        entity.setExternalModelId(dto.externalModelId());
        entity.setCanonicalSlug(dto.canonicalSlug());
        entity.setProviderCode(dto.providerCode());
        entity.setProviderName(dto.providerName());
        entity.setDisplayName(dto.displayName());
        entity.setTagline(dto.tagline());
        entity.setDescription(dto.description());
        entity.setLogoUrl(dto.logoUrl());
        entity.setCoverUrl(dto.coverUrl());
        entity.setTagsJson(toJson(dto.tags()));
        entity.setUseCasesJson(toJson(dto.useCases()));
        entity.setCapabilitiesJson(toJson(dto.capabilities()));
        entity.setContextWindow(dto.contextWindow());
        entity.setMaxOutputTokens(dto.maxOutputTokens());
        entity.setInputModalitiesJson(toJson(dto.inputModalities()));
        entity.setOutputModalitiesJson(toJson(dto.outputModalities()));
        entity.setLanguagesJson(toJson(dto.languages()));
        entity.setDefaultParamsJson(toJson(dto.defaultParams()));
        entity.setParamSchemaJson(toJson(dto.paramSchema()));
        entity.setSafetyPolicyJson(toJson(dto.safetyPolicy()));
        entity.setPricingJson(toJson(dto.pricing()));
        entity.setSupportedParametersJson(toJson(dto.supportedParameters()));
        entity.setVisibility(defaultString(dto.visibility(), "PUBLIC"));
        entity.setPublishStatus(defaultString(dto.publishStatus(), "DRAFT"));
        entity.setStatus(dto.enabled() ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
        entity.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);
        entity.setFeatured(dto.featured());
        entity.setOwnerUserId(dto.ownerUserId());
        entity.setOwnerTeam(dto.ownerTeam());
        entity.setQualityLevel(dto.qualityLevel());
        entity.setLatencyLevel(dto.latencyLevel());
        entity.setCostLevel(dto.costLevel());
        entity.setPricingSummary(dto.pricingSummary());
        entity.setRemark(dto.remark());
        return entity;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public LogicalModelDto withMappings(LogicalModelDto base, List<ProviderModelMappingDto> mappings) {
        return new LogicalModelDto(
                base.id(), base.modelCode(), base.modelName(), base.modelType(), base.modelFamily(), base.version(),
                base.externalSourceCode(), base.externalModelId(), base.canonicalSlug(), base.providerCode(), base.providerName(),
                base.displayName(), base.tagline(), base.description(), base.logoUrl(), base.coverUrl(),
                base.tags(), base.useCases(), base.capabilities(), base.contextWindow(), base.maxOutputTokens(),
                base.inputModalities(), base.outputModalities(), base.languages(),
                base.defaultParams(), base.paramSchema(), base.safetyPolicy(), base.pricing(), base.supportedParameters(),
                base.visibility(), base.publishStatus(), base.enabled(), base.sortOrder(), base.featured(),
                base.ownerUserId(), base.ownerTeam(), base.qualityLevel(), base.latencyLevel(), base.costLevel(),
                base.pricingSummary(), base.remark(), base.createdAt(), base.updatedAt(),
                mappings != null ? mappings : List.of()
        );
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public LogicalModelItem toVO(LogicalModelDto dto) {
        if (dto == null) {
            return null;
        }
        return new LogicalModelItem(
                dto.id(), dto.modelCode(), dto.modelName(), dto.modelType(), dto.modelFamily(), dto.version(),
                dto.externalSourceCode(), dto.externalModelId(), dto.canonicalSlug(), dto.providerCode(), dto.providerName(),
                dto.displayName(), dto.tagline(), dto.description(), dto.logoUrl(), dto.coverUrl(),
                dto.tags(), dto.useCases(), dto.capabilities(), dto.contextWindow(), dto.maxOutputTokens(),
                dto.inputModalities(), dto.outputModalities(), dto.languages(),
                dto.defaultParams(), dto.paramSchema(), dto.safetyPolicy(), dto.pricing(), dto.supportedParameters(),
                dto.visibility(), dto.publishStatus(), dto.enabled(), dto.sortOrder(), dto.featured(),
                dto.ownerUserId(), dto.ownerTeam(), dto.qualityLevel(), dto.latencyLevel(), dto.costLevel(),
                dto.pricingSummary(), dto.remark(), dto.createdAt(), dto.updatedAt(),
                dto.mappings() == null ? List.of() : dto.mappings().stream().map(this::toMappingVO).toList()
        );
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public LogicalModelDto toDto(LogicalModelItem item) {
        return new LogicalModelDto(
                item.id(), item.modelCode(), item.modelName(), item.modelType(), item.modelFamily(), item.version(),
                item.externalSourceCode(), item.externalModelId(), item.canonicalSlug(), item.providerCode(), item.providerName(),
                item.displayName(), item.tagline(), item.description(), item.logoUrl(), item.coverUrl(),
                safeList(item.tags()), safeList(item.useCases()), safeMap(item.capabilities()),
                item.contextWindow(), item.maxOutputTokens(), safeList(item.inputModalities()),
                safeList(item.outputModalities()), safeList(item.languages()), safeMap(item.defaultParams()),
                safeMap(item.paramSchema()), safeMap(item.safetyPolicy()), safeMap(item.pricing()), safeList(item.supportedParameters()),
                item.visibility(), item.publishStatus(),
                item.enabled(), item.sortOrder(), item.featured(), item.ownerUserId(), item.ownerTeam(),
                item.qualityLevel(), item.latencyLevel(), item.costLevel(), item.pricingSummary(), item.remark(),
                item.createdAt(), item.updatedAt(),
                item.mappings() == null ? List.of() : item.mappings().stream().map(this::toMappingDto).toList()
        );
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public List<LogicalModelItem> toVOList(List<LogicalModelDto> list) {
        return list.stream().map(this::toVO).toList();
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public ProviderModelMappingDto toMappingDto(ProviderModelMappingEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ProviderModelMappingDto(
                entity.getId(),
                entity.getLogicalModelId(),
                entity.getLogicalModelCode(),
                entity.getLogicalModelName(),
                entity.getProviderId(),
                entity.getProviderName(),
                entity.getModelId(),
                entity.getModelCode(),
                entity.getModelName(),
                entity.getProviderModelName(),
                EntityStatusEnum.ENABLED.code().equals(entity.getStatus()),
                entity.getPriority()
        );
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public ProviderModelMappingEntity toMappingEntity(ProviderModelMappingDto dto) {
        ProviderModelMappingEntity entity = new ProviderModelMappingEntity();
        entity.setId(dto.id());
        entity.setLogicalModelId(dto.logicalModelId());
        entity.setProviderId(dto.providerId());
        entity.setModelId(dto.modelId());
        entity.setProviderModelName(dto.providerModelName());
        entity.setStatus(dto.enabled() ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
        entity.setPriority(dto.priority() != null ? dto.priority() : 100);
        return entity;
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public ProviderModelMappingItem toMappingVO(ProviderModelMappingDto dto) {
        if (dto == null) {
            return null;
        }
        return new ProviderModelMappingItem(
                dto.id(), dto.logicalModelId(), dto.logicalModelCode(), dto.logicalModelName(),
                dto.providerId(), dto.providerName(),
                dto.modelId(), dto.modelCode(), dto.modelName(), dto.providerModelName(),
                dto.enabled(), dto.priority()
        );
    }

    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public ProviderModelMappingDto toMappingDto(ProviderModelMappingItem item) {
        return new ProviderModelMappingDto(
                item.id(), item.logicalModelId(), item.logicalModelCode(), item.logicalModelName(),
                item.providerId(), item.providerName(),
                item.modelId(), item.modelCode(), item.modelName(), item.providerModelName(),
                item.enabled(), item.priority()
        );
    }

    private static List<String> toStringList(String json) {
        List<String> parsed = JsonKit.fromJson(json, new TypeReference<>() {});
        return parsed != null ? parsed : List.of();
    }

    private static Map<String, Object> toMap(String json) {
        Map<String, Object> parsed = JsonKit.fromJson(json, new TypeReference<>() {});
        return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
    }

    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        return JsonKit.toJson(value);
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<String> safeList(List<String> list) {
        return list != null ? list : List.of();
    }

    private static Map<String, Object> safeMap(Map<String, Object> map) {
        return map != null ? map : new LinkedHashMap<>();
    }
}
