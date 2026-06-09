package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.plugin.PluginDto;
import org.chobit.knot.gateway.vo.plugin.PluginItem;
import org.chobit.knot.gateway.entity.PluginInstanceEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface PluginConverter {

    PluginDto toDto(PluginInstanceEntity entity);

    List<PluginDto> toDtoList(List<PluginInstanceEntity> entities);

    PluginItem toVO(PluginDto dto);

    PluginDto toDto(PluginItem vo);

    List<PluginItem> toVOList(List<PluginDto> dtos);
}
