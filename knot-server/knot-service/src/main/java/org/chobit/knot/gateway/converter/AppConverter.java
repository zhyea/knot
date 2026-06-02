package org.chobit.knot.gateway.converter;



import org.chobit.knot.gateway.dto.app.AppDto;

import org.chobit.knot.gateway.entity.AppEntity;

import org.chobit.knot.gateway.vo.app.AppItem;

import org.mapstruct.Mapper;

import org.mapstruct.Mapping;



import java.util.List;



@Mapper(componentModel = "spring")

public interface AppConverter {



    @Mapping(source = "ownerRealName", target = "ownerName")
    @Mapping(target = "rateLimitPolicy", ignore = true)
    @Mapping(target = "quotaPolicy", ignore = true)
    AppDto toDto(AppEntity entity);



    @Mapping(target = "status", constant = "ENABLED")

    @Mapping(target = "ownerRealName", ignore = true)

    @Mapping(target = "isDeleted", ignore = true)

    AppEntity toEntity(AppDto dto);



    List<AppDto> toDtoList(List<AppEntity> entities);



    AppItem toVO(AppDto dto);



    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "ownerName", ignore = true)

    AppDto toDto(AppItem vo);



    List<AppItem> toVOList(List<AppDto> dtos);

}

