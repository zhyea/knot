package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.provider.ProviderDto;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.vo.provider.ProviderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface ProviderConverter {

    @Mapping(source = "providerType", target = "type")
    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(source = "rateLimitJson", target = "rateLimitPolicy", qualifiedByName = "jsonToRateLimit")
    @Mapping(source = "quotaJson", target = "quotaPolicy", qualifiedByName = "jsonToQuota")
    ProviderDto toDto(ProviderEntity entity);

    @Mapping(source = "type", target = "providerType")
    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(source = "rateLimitPolicy", target = "rateLimitJson", qualifiedByName = "rateLimitToJson")
    @Mapping(source = "quotaPolicy", target = "quotaJson", qualifiedByName = "quotaToJson")
    ProviderEntity toEntity(ProviderDto dto);

    List<ProviderDto> toDtoList(List<ProviderEntity> entities);

    ProviderItem toVO(ProviderDto dto);

    @Mapping(target = "code", ignore = true)
    ProviderDto toDto(ProviderItem vo);

    List<ProviderItem> toVOList(List<ProviderDto> dtos);
}
