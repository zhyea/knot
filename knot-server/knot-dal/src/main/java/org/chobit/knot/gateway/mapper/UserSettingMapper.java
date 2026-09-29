package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.chobit.knot.gateway.entity.UserSettingEntity;

import java.util.List;

@Mapper
public interface UserSettingMapper {

    List<UserSettingEntity> listByUserId(Long userId);

    int upsert(UserSettingEntity entity);
}
