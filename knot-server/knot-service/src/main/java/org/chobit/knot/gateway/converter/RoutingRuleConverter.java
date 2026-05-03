package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.routing.RoutingRuleDto;
import org.chobit.knot.gateway.dto.routing.RoutingSwitchLogDto;
import org.chobit.knot.gateway.entity.RoutingHitLogEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.chobit.knot.gateway.vo.routing.RoutingRule;
import org.chobit.knot.gateway.vo.routing.RoutingSwitchLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface RoutingRuleConverter {

    // ==================== Entity ↔ DTO ====================

    @Mapping(source = "strategyType", target = "strategy")
    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    RoutingRuleDto toDto(RoutingRuleEntity entity);

    @Mapping(source = "strategy", target = "strategyType")
    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    RoutingRuleEntity toEntity(RoutingRuleDto dto);

    List<RoutingRuleDto> toDtoList(List<RoutingRuleEntity> entities);

    // ==================== HitLog Entity → DTO ====================

    RoutingSwitchLogDto toSwitchLogDto(RoutingHitLogEntity entity);

    List<RoutingSwitchLogDto> toSwitchLogDtoList(List<RoutingHitLogEntity> entities);

    // ==================== DTO ↔ VO ====================

    RoutingRule toVO(RoutingRuleDto dto);

    RoutingRuleDto toDto(RoutingRule vo);

    List<RoutingRule> toVOList(List<RoutingRuleDto> dtos);

    RoutingSwitchLog toSwitchLogVO(RoutingSwitchLogDto dto);

    List<RoutingSwitchLog> toSwitchLogVOList(List<RoutingSwitchLogDto> dtos);
}
