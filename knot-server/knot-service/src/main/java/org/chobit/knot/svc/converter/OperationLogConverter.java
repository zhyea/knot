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
     * <p>⭐ <b>刻意跳过 Dto</b>：{@link OperationLogDetailItem} 与实体的字段逐字同名，
     * 中间那层 Dto 的唯一作用是把 4 个字段改别名（那是看板瘦投影的口径），对完整行毫无意义。
     * 硬插一层就是纯拷贝——判据同 {@code AuthorizationConverter} 的类注释。</p>
     */
    OperationLogDetailItem toDetailItem(OperationLogEntity entity);

    List<OperationLogDetailItem> toDetailItemList(List<OperationLogEntity> entities);

}
