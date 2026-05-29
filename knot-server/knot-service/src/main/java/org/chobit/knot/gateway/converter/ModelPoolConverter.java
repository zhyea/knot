package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.model.ModelPoolDto;
import org.chobit.knot.gateway.dto.model.ModelPoolItemDto;
import org.chobit.knot.gateway.entity.ModelPoolEntity;
import org.chobit.knot.gateway.entity.ModelPoolItemEntity;
import org.chobit.knot.gateway.vo.model.ModelPool;
import org.chobit.knot.gateway.vo.model.ModelPoolItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface ModelPoolConverter {

    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(target = "items", ignore = true)
    ModelPoolDto toDto(ModelPoolEntity entity);

    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    ModelPoolEntity toEntity(ModelPoolDto dto);

    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    ModelPoolItemDto toItemDto(ModelPoolItemEntity entity);

    ModelPool toVO(ModelPoolDto dto);

    ModelPoolDto toDto(ModelPool vo);

    List<ModelPool> toVOList(List<ModelPoolDto> dtos);
}
