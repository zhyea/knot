package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.grayrelease.GrayPlanDto;
import org.chobit.knot.gateway.vo.grayrelease.GrayPlan;
import org.chobit.knot.gateway.entity.GrayPlanEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface GrayReleaseConverter {

    // ==================== Entity → DTO ====================

    @Mapping(source = "stepsJson", target = "steps", qualifiedByName = "jsonToSteps")
    GrayPlanDto toDto(GrayPlanEntity entity);

    List<GrayPlanDto> toDtoList(List<GrayPlanEntity> entities);

    // ==================== DTO ↔ VO ====================

    GrayPlan toVO(GrayPlanDto dto);

    List<GrayPlan> toVOList(List<GrayPlanDto> dtos);
}
