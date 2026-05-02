package com.knot.gateway.converter;

import com.knot.gateway.controller.RoutingRuleController;
import com.knot.gateway.entity.RoutingHitLogEntity;
import com.knot.gateway.entity.RoutingRuleEntity;
import com.knot.gateway.service.RoutingRuleService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface RoutingRuleConverter {

    // ==================== Entity ↔ DTO ====================

    @Mapping(source = "strategyType", target = "strategy")
    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    RoutingRuleService.RoutingRuleDto toDto(RoutingRuleEntity entity);

    @Mapping(source = "strategy", target = "strategyType")
    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    RoutingRuleEntity toEntity(RoutingRuleService.RoutingRuleDto dto);

    List<RoutingRuleService.RoutingRuleDto> toDtoList(List<RoutingRuleEntity> entities);

    // ==================== HitLog Entity → DTO ====================

    RoutingRuleService.RoutingSwitchLogDto toSwitchLogDto(RoutingHitLogEntity entity);

    List<RoutingRuleService.RoutingSwitchLogDto> toSwitchLogDtoList(List<RoutingHitLogEntity> entities);

    // ==================== DTO ↔ VO ====================

    RoutingRuleController.RoutingRule toVO(RoutingRuleService.RoutingRuleDto dto);

    RoutingRuleService.RoutingRuleDto toDto(RoutingRuleController.RoutingRule vo);

    List<RoutingRuleController.RoutingRule> toVOList(List<RoutingRuleService.RoutingRuleDto> dtos);

    RoutingRuleController.RoutingSwitchLog toSwitchLogVO(RoutingRuleService.RoutingSwitchLogDto dto);

    List<RoutingRuleController.RoutingSwitchLog> toSwitchLogVOList(List<RoutingRuleService.RoutingSwitchLogDto> dtos);
}
