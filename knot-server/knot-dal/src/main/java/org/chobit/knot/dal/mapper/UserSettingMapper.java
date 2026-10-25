package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.chobit.knot.dal.entity.UserSettingEntity;

import java.util.List;

@Mapper
public interface UserSettingMapper {

    List<UserSettingEntity> listByUserId(Long userId);

    int upsert(UserSettingEntity entity);
}
