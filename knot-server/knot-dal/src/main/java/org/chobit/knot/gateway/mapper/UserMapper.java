package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper {

    List<UserEntity> listUsers();

    UserEntity getUserById(Long id);

    int insertUser(UserEntity entity);

    int updateUserStatus(UserEntity entity);
}
