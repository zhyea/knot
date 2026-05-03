package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.security.AlertItemDto;
import org.chobit.knot.gateway.dto.security.CacheEvictResultDto;
import org.chobit.knot.gateway.dto.security.SecurityOverviewDto;
import org.chobit.knot.gateway.dto.security.SecurityPolicyDto;
import org.chobit.knot.gateway.entity.AlertEntity;
import org.chobit.knot.gateway.entity.SecurityPolicyEntity;
import org.chobit.knot.gateway.vo.security.AlertItem;
import org.chobit.knot.gateway.vo.security.CacheEvictResult;
import org.chobit.knot.gateway.vo.security.SecurityOverview;
import org.chobit.knot.gateway.vo.security.SecurityPolicy;
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
