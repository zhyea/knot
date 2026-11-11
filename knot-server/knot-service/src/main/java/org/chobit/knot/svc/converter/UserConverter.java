package org.chobit.knot.svc.converter;

import org.chobit.knot.svc.dto.user.UserDto;
import org.chobit.knot.dal.entity.UserEntity;
import org.chobit.knot.svc.vo.user.UserItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = SharedMappings.class)
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

    // Item → DTO：入参装配归转换器，controller 不再手拼 12 个位置参数
    // （手拼的代价是「哪个位置该填 null」只存在于调用点，加字段时编译器帮不上忙）

    /**
     * 创建入参：主键与所有派生只读字段由服务端生成，不从请求体带入。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "roleNames", ignore = true)
    @Mapping(target = "lastLoginTime", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "boundCount", ignore = true)
    UserDto toCreateDto(UserItem item);

    /**
     * 更新入参：主键来自路径；{@code password} 不走更新通道（改密见 reset-password）。
     *
     * <p>登录名允许随更新一起改（未绑定引用时），所以 {@code username} 照常透传。</p>
     */
    @Mapping(target = "id", source = "id")
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "roleNames", ignore = true)
    @Mapping(target = "lastLoginTime", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "boundCount", ignore = true)
    UserDto toUpdateDto(Long id, UserItem item);
}
