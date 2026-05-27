package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.UserSettingEntity;

import java.util.List;

@Mapper
public interface UserSettingMapper {

    List<UserSettingEntity> listByUserId(Long userId);

    UserSettingEntity getByUserIdAndKey(@Param("userId") Long userId, @Param("settingKey") String settingKey);

    int upsert(UserSettingEntity entity);

    int deleteByUserIdAndKey(@Param("userId") Long userId, @Param("settingKey") String settingKey);
}
