package org.chobit.knot.svc.converter;



import org.chobit.knot.svc.dto.app.AppDto;

import org.chobit.knot.dal.entity.AppEntity;

import org.chobit.knot.svc.vo.app.AppItem;
import org.chobit.knot.svc.vo.app.AppListItem;

import org.mapstruct.Mapper;

import org.mapstruct.Mapping;



import java.util.List;



@Mapper(componentModel = "spring")

public interface AppConverter {



    @Mapping(source = "ownerRealName", target = "ownerName")
    @Mapping(target = "rateLimitPolicy", ignore = true)
    @Mapping(target = "quotaPolicies", ignore = true)
    AppDto toDto(AppEntity entity);



    @Mapping(target = "status", constant = "1")

    @Mapping(target = "ownerRealName", ignore = true)

    @Mapping(target = "isDeleted", ignore = true)

    AppEntity toEntity(AppDto dto);



    List<AppDto> toDtoList(List<AppEntity> entities);



    AppItem toVO(AppDto dto);



    @Mapping(target = "deptName", ignore = true)
    @Mapping(target = "ownerName", ignore = true)

    AppDto toDto(AppItem vo);



    List<AppItem> toVOList(List<AppDto> dtos);

    /** 列表专用视图：不映射额度 / 频控等仅供编辑的策略字段 */
    AppListItem toListItemVO(AppDto dto);

    List<AppListItem> toListItemVOList(List<AppDto> dtos);

}

