package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.provider.ProviderAccountDto;
import org.chobit.knot.gateway.entity.ProviderAccountEntity;
import org.chobit.knot.gateway.vo.provider.ProviderAccountDetail;
import org.chobit.knot.gateway.vo.provider.ProviderAccountItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class,
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface ProviderConverter {

    @Mapping(source = "providerCode", target = "type")
    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(target = "rateLimitPolicy", ignore = true)
    @Mapping(target = "quotaPolicy", ignore = true)
    @Mapping(target = "credentialType", ignore = true)
    @Mapping(target = "authConfig", ignore = true)
    ProviderAccountDto toDto(ProviderAccountEntity entity);

    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(target = "providerCode", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProviderAccountEntity toEntity(ProviderAccountDto dto);

    List<ProviderAccountDto> toDtoList(List<ProviderAccountEntity> entities);

    /**
     * 列表项：只映射列表展示所需字段，认证配置与策略不参与。
     */
    ProviderAccountItem toVO(ProviderAccountDto dto);

    /**
     * 详情：详情查询响应，同时承接新建与更新的请求体。
     */
    ProviderAccountDetail toDetail(ProviderAccountDto dto);

    ProviderAccountDto toDto(ProviderAccountDetail vo);

    List<ProviderAccountItem> toVOList(List<ProviderAccountDto> dtos);
}
