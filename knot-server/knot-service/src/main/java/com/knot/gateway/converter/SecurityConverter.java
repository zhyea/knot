package com.knot.gateway.converter;

import com.knot.gateway.dto.security.AlertItemDto;
import com.knot.gateway.vo.security.*;
import com.knot.gateway.dto.security.CacheEvictResultDto;
import com.knot.gateway.dto.security.SecurityOverviewDto;
import com.knot.gateway.dto.security.SecurityPolicyDto;
import com.knot.gateway.entity.AlertEntity;
import com.knot.gateway.entity.SecurityPolicyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface SecurityConverter {

    // ==================== Entity → DTO ====================

    SecurityPolicyDto toPolicyDto(SecurityPolicyEntity entity);

    @Mapping(source = "id", target = "alertId", qualifiedByName = "idToAlertId")
    AlertItemDto toAlertItemDto(AlertEntity entity);

    List<AlertItemDto> toAlertItemDtoList(List<AlertEntity> entities);

    // ==================== DTO ↔ VO ====================

    SecurityOverview toOverviewVO(SecurityOverviewDto dto);

    SecurityPolicy toPolicyVO(SecurityPolicyDto dto);

    SecurityPolicyDto toPolicyDto(SecurityPolicy vo);

    AlertItem toAlertVO(AlertItemDto dto);

    List<AlertItem> toAlertVOList(List<AlertItemDto> dtos);

    CacheEvictResult toCacheEvictVO(CacheEvictResultDto dto);
}
