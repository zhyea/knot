package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.provider.ProviderAccountDto;
import org.chobit.knot.gateway.entity.ProviderAccountEntity;
import org.chobit.knot.gateway.vo.provider.ProviderAccountItem;
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
    @Mapping(target = "credentialType", ignore = true)
    @Mapping(target = "authConfig", ignore = true)
    ProviderAccountDto toDto(ProviderAccountEntity entity);

    @Mapping(source = "type", target = "providerType")
    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProviderAccountEntity toEntity(ProviderAccountDto dto);

    List<ProviderAccountDto> toDtoList(List<ProviderAccountEntity> entities);

    ProviderAccountItem toVO(ProviderAccountDto dto);

    ProviderAccountDto toDto(ProviderAccountItem vo);

    List<ProviderAccountItem> toVOList(List<ProviderAccountDto> dtos);
}
