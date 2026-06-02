package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.system.DepartmentDto;
import org.chobit.knot.gateway.entity.DepartmentEntity;
import org.chobit.knot.gateway.vo.system.DepartmentItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface DepartmentConverter {

    DepartmentDto toDto(DepartmentEntity entity);

    List<DepartmentDto> toDtoList(List<DepartmentEntity> entities);

    DepartmentEntity toEntity(DepartmentDto dto);

    @Mapping(target = "children", ignore = true)
    DepartmentItem toVO(DepartmentDto dto);

    List<DepartmentItem> toVOList(List<DepartmentDto> dtos);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DepartmentDto toDto(DepartmentItem item);
}
