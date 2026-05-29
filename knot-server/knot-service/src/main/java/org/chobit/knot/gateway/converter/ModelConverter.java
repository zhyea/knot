package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.model.ModelDto;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.vo.model.ModelItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface ModelConverter {

    // ==================== Entity ↔ DTO ====================

    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(target = "logicalModelId", ignore = true)
    @Mapping(target = "rateLimitPolicy", ignore = true)
    @Mapping(target = "quotaPolicy", ignore = true)
    ModelDto toDto(ModelEntity entity);

    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(target = "providerName", ignore = true)
    @Mapping(target = "billingRuleName", ignore = true)
    ModelEntity toEntity(ModelDto dto);

    List<ModelDto> toDtoList(List<ModelEntity> entities);

    // ==================== DTO ↔ VO ====================

    ModelItem toVO(ModelDto dto);

    @Mapping(target = "providerName", ignore = true)
    @Mapping(target = "billingRuleName", ignore = true)
    ModelDto toDto(ModelItem vo);

    List<ModelItem> toVOList(List<ModelDto> dtos);
}
