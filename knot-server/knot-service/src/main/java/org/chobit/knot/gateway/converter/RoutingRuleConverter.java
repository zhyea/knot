package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.routing.RoutingRuleDto;
import org.chobit.knot.gateway.dto.routing.RoutingRuleModelDto;
import org.chobit.knot.gateway.dto.routing.RoutingSwitchLogDto;
import org.chobit.knot.gateway.entity.RoutingHitLogEntity;
import org.chobit.knot.gateway.vo.routing.RoutingRule;
import org.chobit.knot.gateway.vo.routing.RoutingRuleModelItem;
import org.chobit.knot.gateway.vo.routing.RoutingSwitchLog;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoutingRuleConverter {

    RoutingRule toVO(RoutingRuleDto dto);

    RoutingRuleDto toDto(RoutingRule vo);

    List<RoutingRule> toVOList(List<RoutingRuleDto> dtos);

    RoutingRuleModelItem toModelVO(RoutingRuleModelDto dto);

    List<RoutingRuleModelItem> toModelVOList(List<RoutingRuleModelDto> dtos);

    RoutingRuleModelDto toModelDto(RoutingRuleModelItem item);

    List<RoutingRuleModelDto> toModelDtoList(List<RoutingRuleModelItem> items);

    RoutingSwitchLogDto toSwitchLogDto(RoutingHitLogEntity entity);

    List<RoutingSwitchLogDto> toSwitchLogDtoList(List<RoutingHitLogEntity> entities);

    RoutingSwitchLog toSwitchLogVO(RoutingSwitchLogDto dto);

    List<RoutingSwitchLog> toSwitchLogVOList(List<RoutingSwitchLogDto> dtos);
}
