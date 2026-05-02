package com.knot.gateway.converter;

import com.knot.gateway.controller.AppController;
import com.knot.gateway.entity.AppEntity;
import com.knot.gateway.service.AppService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface AppConverter {

    // ==================== Entity ↔ DTO ====================

    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(source = "rateLimitJson", target = "rateLimitPolicy", qualifiedByName = "jsonToRateLimit")
    @Mapping(source = "quotaJson", target = "quotaPolicy", qualifiedByName = "jsonToQuota")
    AppService.AppDto toDto(AppEntity entity);

    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(source = "rateLimitPolicy", target = "rateLimitJson", qualifiedByName = "rateLimitToJson")
    @Mapping(source = "quotaPolicy", target = "quotaJson", qualifiedByName = "quotaToJson")
    AppEntity toEntity(AppService.AppDto dto);

    List<AppService.AppDto> toDtoList(List<AppEntity> entities);

    // ==================== DTO ↔ VO ====================

    @Mapping(source = "ownerUserId", target = "owner")
    AppController.AppItem toVO(AppService.AppDto dto);

    AppService.AppDto toDto(AppController.AppItem vo);

    List<AppController.AppItem> toVOList(List<AppService.AppDto> dtos);
}
