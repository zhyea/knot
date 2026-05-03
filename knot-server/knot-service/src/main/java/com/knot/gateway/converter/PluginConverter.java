package com.knot.gateway.converter;

import com.knot.gateway.dto.plugin.PluginDto;
import com.knot.gateway.vo.plugin.PluginItem;
import com.knot.gateway.entity.PluginEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface PluginConverter {

    PluginDto toDto(PluginEntity entity);

    List<PluginDto> toDtoList(List<PluginEntity> entities);

    PluginItem toVO(PluginDto dto);

    PluginDto toDto(PluginItem vo);

    List<PluginItem> toVOList(List<PluginDto> dtos);
}
