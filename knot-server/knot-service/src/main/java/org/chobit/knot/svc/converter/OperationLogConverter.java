package org.chobit.knot.svc.converter;

import org.chobit.knot.svc.dto.system.OperationLogDto;
import org.chobit.knot.dal.entity.OperationLogEntity;
import org.chobit.knot.svc.vo.system.OperationLogDetailItem;
import org.chobit.knot.svc.vo.system.OperationLogItem;
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

    // ==================== Entity → VO（完整行，跳过 Dto） ====================

    /**
     * 日志页 / 实体操作记录抽屉用的完整行。
     *
     * {@link OperationLogDetailItem} 与实体字段逐字同名，故不设中间 Dto。
     */
    OperationLogDetailItem toDetailItem(OperationLogEntity entity);

    List<OperationLogDetailItem> toDetailItemList(List<OperationLogEntity> entities);

}
