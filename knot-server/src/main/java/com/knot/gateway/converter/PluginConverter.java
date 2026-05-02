package com.knot.gateway.converter;

import com.knot.gateway.controller.PluginController;
import com.knot.gateway.entity.PluginEntity;
import com.knot.gateway.service.PluginService;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface PluginConverter {

    PluginService.PluginDto toDto(PluginEntity entity);

    List<PluginService.PluginDto> toDtoList(List<PluginEntity> entities);

    PluginController.PluginItem toVO(PluginService.PluginDto dto);

    PluginService.PluginDto toDto(PluginController.PluginItem vo);

    List<PluginController.PluginItem> toVOList(List<PluginService.PluginDto> dtos);
}
