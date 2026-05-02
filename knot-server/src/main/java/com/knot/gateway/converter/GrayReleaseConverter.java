package com.knot.gateway.converter;

import com.knot.gateway.controller.GrayReleaseController;
import com.knot.gateway.entity.GrayPlanEntity;
import com.knot.gateway.service.GrayReleaseService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface GrayReleaseConverter {

    // ==================== Entity → DTO ====================

    @Mapping(source = "stepsJson", target = "steps", qualifiedByName = "jsonToSteps")
    GrayReleaseService.GrayPlanDto toDto(GrayPlanEntity entity);

    List<GrayReleaseService.GrayPlanDto> toDtoList(List<GrayPlanEntity> entities);

    // ==================== DTO ↔ VO ====================

    GrayReleaseController.GrayPlan toVO(GrayReleaseService.GrayPlanDto dto);

    List<GrayReleaseController.GrayPlan> toVOList(List<GrayReleaseService.GrayPlanDto> dtos);
}
