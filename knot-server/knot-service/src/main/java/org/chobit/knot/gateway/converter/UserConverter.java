package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.user.UserDto;
import org.chobit.knot.gateway.entity.UserEntity;
import org.chobit.knot.gateway.vo.user.UserItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface UserConverter {

    // Entity ↔ DTO
    // password 只作创建/更新的入参通道，读路径不回显（实体侧是 passwordHash，本就无从映射）
    @Mapping(target = "password", ignore = true)
    UserDto toDto(UserEntity entity);

    List<UserDto> toDtoList(List<UserEntity> entities);

    // DTO ↔ VO
    @Mapping(target = "password", ignore = true)
    UserItem toVO(UserDto dto);

    List<UserItem> toVOList(List<UserDto> dtos);
}
