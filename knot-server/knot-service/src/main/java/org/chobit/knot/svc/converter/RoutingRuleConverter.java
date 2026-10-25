package org.chobit.knot.svc.converter;

import org.chobit.knot.svc.dto.routing.RoutingRuleDto;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.vo.routing.RoutingRule;
import org.chobit.knot.svc.vo.routing.RoutingRuleTargetItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoutingRuleConverter {

    RoutingRule toVO(RoutingRuleDto dto);

    RoutingRuleDto toDto(RoutingRule vo);

    List<RoutingRule> toVOList(List<RoutingRuleDto> dtos);

    RoutingRuleTargetItem toTargetVO(RoutingRuleTargetDto dto);

    List<RoutingRuleTargetItem> toTargetVOList(List<RoutingRuleTargetDto> dtos);

    RoutingRuleTargetDto toTargetDto(RoutingRuleTargetItem item);

    List<RoutingRuleTargetDto> toTargetDtoList(List<RoutingRuleTargetItem> items);

}
