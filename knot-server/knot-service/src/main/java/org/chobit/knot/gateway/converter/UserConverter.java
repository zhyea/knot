package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.user.UserDto;
import org.chobit.knot.gateway.entity.UserEntity;
import org.chobit.knot.gateway.vo.user.UserItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface UserConverter {

    // Entity ↔ DTO
    UserDto toDto(UserEntity entity);
    List<UserDto> toDtoList(List<UserEntity> entities);

    // DTO ↔ VO
    UserItem toVO(UserDto dto);
    List<UserItem> toVOList(List<UserDto> dtos);
}
