package com.knot.gateway.converter;

import com.knot.gateway.controller.ModelController;
import com.knot.gateway.entity.ModelEntity;
import com.knot.gateway.service.ModelService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface ModelConverter {

    // ==================== Entity ↔ DTO ====================

    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(source = "rateLimitJson", target = "rateLimitPolicy", qualifiedByName = "jsonToRateLimit")
    @Mapping(source = "quotaJson", target = "quotaPolicy", qualifiedByName = "jsonToQuota")
    ModelService.ModelDto toDto(ModelEntity entity);

    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(source = "rateLimitPolicy", target = "rateLimitJson", qualifiedByName = "rateLimitToJson")
    @Mapping(source = "quotaPolicy", target = "quotaJson", qualifiedByName = "quotaToJson")
    ModelEntity toEntity(ModelService.ModelDto dto);

    List<ModelService.ModelDto> toDtoList(List<ModelEntity> entities);

    // ==================== DTO ↔ VO ====================

    ModelController.ModelItem toVO(ModelService.ModelDto dto);

    @Mapping(target = "modelCode", ignore = true)
    ModelService.ModelDto toDto(ModelController.ModelItem vo);

    List<ModelController.ModelItem> toVOList(List<ModelService.ModelDto> dtos);
}
