package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.system.*;
import org.chobit.knot.gateway.entity.*;
import org.chobit.knot.gateway.vo.system.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface SystemConverter {

    // ==================== Entity ↔ DTO ====================

    OperationLogDto toOperationLogDto(OperationLogEntity entity);

    @Mapping(source = "logId", target = "id")
    OperationLogDetailDto toOperationLogDetailDto(OperationLogDetailEntity entity);

    List<OperationLogDto> toOperationLogDtoList(List<OperationLogEntity> entities);

    // ==================== DTO ↔ VO ====================

    OperationLogItem toOperationLogVO(OperationLogDto dto);

    OperationLogDetail toOperationLogDetailVO(OperationLogDetailDto dto);

    List<OperationLogItem> toOperationLogVOList(List<OperationLogDto> dtos);

}
