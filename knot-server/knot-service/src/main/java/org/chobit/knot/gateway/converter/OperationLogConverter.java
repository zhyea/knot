package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.system.OperationLogDto;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.vo.system.OperationLogItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = SharedMappings.class)
public interface OperationLogConverter {

    // ==================== Entity ↔ DTO ====================

    // 实体字段与 DTO 是同义不同名：module/operation/entityId/status → moduleCode/actionCode/targetId/resultStatus。
    // 不显式映射会让这 4 个字段恒为 null（/api/system/logs、/api/system/operation-logs 曾因此返回空壳数据）。
    @Mapping(source = "module", target = "moduleCode")
    @Mapping(source = "operation", target = "actionCode")
    @Mapping(source = "entityId", target = "targetId")
    @Mapping(source = "status", target = "resultStatus")
    OperationLogDto toOperationLogDto(OperationLogEntity entity);

    List<OperationLogDto> toOperationLogDtoList(List<OperationLogEntity> entities);

    // ==================== DTO ↔ VO ====================

    OperationLogItem toOperationLogVO(OperationLogDto dto);

    List<OperationLogItem> toOperationLogVOList(List<OperationLogDto> dtos);

}
