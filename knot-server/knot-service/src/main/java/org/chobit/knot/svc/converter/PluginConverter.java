package org.chobit.knot.svc.converter;

import org.chobit.knot.svc.dto.plugin.PluginDto;
import org.chobit.knot.svc.vo.plugin.PluginItem;
import org.chobit.knot.dal.entity.PluginInstanceEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = SharedMappings.class)
public interface PluginConverter {

    PluginDto toDto(PluginInstanceEntity entity);

    List<PluginDto> toDtoList(List<PluginInstanceEntity> entities);

    PluginItem toVO(PluginDto dto);

    PluginDto toDto(PluginItem vo);

    List<PluginItem> toVOList(List<PluginDto> dtos);
}
