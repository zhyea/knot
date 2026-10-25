package org.chobit.knot.svc.converter;

import org.chobit.knot.svc.dto.routing.RoutingTestPresetDto;
import org.chobit.knot.dal.entity.RoutingTestPresetEntity;
import org.chobit.knot.svc.vo.routing.RoutingTestPreset;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = SharedMappings.class)
public interface RoutingTestPresetConverter {

    RoutingTestPresetDto toDto(RoutingTestPresetEntity entity);

    List<RoutingTestPresetDto> toDtoList(List<RoutingTestPresetEntity> entities);

    RoutingTestPreset toVO(RoutingTestPresetDto dto);

    RoutingTestPresetDto toDto(RoutingTestPreset vo);

    List<RoutingTestPreset> toVOList(List<RoutingTestPresetDto> dtos);
}
