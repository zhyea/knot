package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.app.AppDto;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.vo.app.AppItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface AppConverter {


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

    @Mapping(source = "owner", target = "ownerUserId")
    AppDto toDto(AppItem vo);

    List<AppItem> toVOList(List<AppDto> dtos);
}
