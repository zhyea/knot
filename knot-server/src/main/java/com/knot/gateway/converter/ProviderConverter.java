package com.knot.gateway.converter;

import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.controller.ProviderController;
import com.knot.gateway.entity.ProviderEntity;
import com.knot.gateway.service.ProviderService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface ProviderConverter {

    // ==================== Entity ↔ DTO ====================

    @Mapping(source = "providerType", target = "type")
    @Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    @Mapping(source = "rateLimitJson", target = "rateLimitPolicy", qualifiedByName = "jsonToRateLimit")
    @Mapping(source = "quotaJson", target = "quotaPolicy", qualifiedByName = "jsonToQuota")
    ProviderService.ProviderDto toDto(ProviderEntity entity);

    @Mapping(source = "type", target = "providerType")
    @Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    @Mapping(source = "rateLimitPolicy", target = "rateLimitJson", qualifiedByName = "rateLimitToJson")
    @Mapping(source = "quotaPolicy", target = "quotaJson", qualifiedByName = "quotaToJson")
    ProviderEntity toEntity(ProviderService.ProviderDto dto);

    List<ProviderService.ProviderDto> toDtoList(List<ProviderEntity> entities);

    // ==================== DTO ↔ VO ====================

    ProviderController.ProviderItem toVO(ProviderService.ProviderDto dto);

    @Mapping(target = "code", ignore = true)
    ProviderService.ProviderDto toDto(ProviderController.ProviderItem vo);

    List<ProviderController.ProviderItem> toVOList(List<ProviderService.ProviderDto> dtos);
}
