package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.provider.ProviderDto;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.vo.provider.ProviderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class,
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface ProviderConverter {

    @Mapping(source = "providerType", target = "type")
    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(target = "rateLimitPolicy", ignore = true)
    @Mapping(target = "quotaPolicy", ignore = true)
    @Mapping(target = "authConfig", ignore = true)
    ProviderDto toDto(ProviderEntity entity);

    @Mapping(source = "type", target = "providerType")
    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    ProviderEntity toEntity(ProviderDto dto);

    List<ProviderDto> toDtoList(List<ProviderEntity> entities);

    ProviderItem toVO(ProviderDto dto);

    ProviderDto toDto(ProviderItem vo);

    List<ProviderItem> toVOList(List<ProviderDto> dtos);
}
