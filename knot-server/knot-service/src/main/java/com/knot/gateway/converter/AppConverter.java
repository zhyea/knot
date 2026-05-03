package com.knot.gateway.converter;

import com.knot.gateway.dto.app.AppDto;
import com.knot.gateway.entity.AppEntity;
import com.knot.gateway.vo.app.AppItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface AppConverter {

    // ==================== Entity ↔ DTO ====================

    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(source = "rateLimitJson", target = "rateLimitPolicy", qualifiedByName = "jsonToRateLimit")
    @Mapping(source = "quotaJson", target = "quotaPolicy", qualifiedByName = "jsonToQuota")
    AppDto toDto(AppEntity entity);

    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(source = "rateLimitPolicy", target = "rateLimitJson", qualifiedByName = "rateLimitToJson")
    @Mapping(source = "quotaPolicy", target = "quotaJson", qualifiedByName = "quotaToJson")
    AppEntity toEntity(AppDto dto);

    List<AppDto> toDtoList(List<AppEntity> entities);

    // ==================== DTO ↔ VO ====================

    @Mapping(source = "ownerUserId", target = "owner")
    AppItem toVO(AppDto dto);

    AppDto toDto(AppItem vo);

    List<AppItem> toVOList(List<AppDto> dtos);
}
