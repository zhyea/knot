package com.knot.gateway.converter;

import com.knot.gateway.dto.model.ModelDto;
import com.knot.gateway.entity.ModelEntity;
import com.knot.gateway.vo.model.ModelItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface ModelConverter {

    // ==================== Entity ↔ DTO ====================

    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(source = "rateLimitJson", target = "rateLimitPolicy", qualifiedByName = "jsonToRateLimit")
    @Mapping(source = "quotaJson", target = "quotaPolicy", qualifiedByName = "jsonToQuota")
    ModelDto toDto(ModelEntity entity);

    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(source = "rateLimitPolicy", target = "rateLimitJson", qualifiedByName = "rateLimitToJson")
    @Mapping(source = "quotaPolicy", target = "quotaJson", qualifiedByName = "quotaToJson")
    ModelEntity toEntity(ModelDto dto);

    List<ModelDto> toDtoList(List<ModelEntity> entities);

    // ==================== DTO ↔ VO ====================

    ModelItem toVO(ModelDto dto);

    @Mapping(target = "modelCode", ignore = true)
    ModelDto toDto(ModelItem vo);

    List<ModelItem> toVOList(List<ModelDto> dtos);
}
