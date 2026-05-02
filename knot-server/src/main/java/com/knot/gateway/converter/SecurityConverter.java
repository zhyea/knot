package com.knot.gateway.converter;

import com.knot.gateway.controller.SecurityController;
import com.knot.gateway.entity.AlertEntity;
import com.knot.gateway.entity.SecurityPolicyEntity;
import com.knot.gateway.service.SecurityService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface SecurityConverter {

    // ==================== Entity → DTO ====================

    SecurityService.SecurityPolicyDto toPolicyDto(SecurityPolicyEntity entity);

    @Mapping(source = "id", target = "alertId", qualifiedByName = "idToAlertId")
    SecurityService.AlertItemDto toAlertItemDto(AlertEntity entity);

    List<SecurityService.AlertItemDto> toAlertItemDtoList(List<AlertEntity> entities);

    // ==================== DTO ↔ VO ====================

    SecurityController.SecurityOverview toOverviewVO(SecurityService.SecurityOverviewDto dto);

    SecurityController.SecurityPolicy toPolicyVO(SecurityService.SecurityPolicyDto dto);

    SecurityService.SecurityPolicyDto toPolicyDto(SecurityController.SecurityPolicy vo);

    SecurityController.AlertItem toAlertVO(SecurityService.AlertItemDto dto);

    List<SecurityController.AlertItem> toAlertVOList(List<SecurityService.AlertItemDto> dtos);

    SecurityController.CacheEvictResult toCacheEvictVO(SecurityService.CacheEvictResultDto dto);
}
