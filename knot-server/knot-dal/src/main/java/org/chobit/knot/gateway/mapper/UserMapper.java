package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    List<UserEntity> listUsers(@Param("keyword") String keyword);

    UserEntity getUserById(Long id);

    UserEntity getUserByUsername(String username);

    Long countByDeptId(Long deptId);

    int insertUser(UserEntity entity);

    int updateUserStatus(UserEntity entity);

    int updateUser(UserEntity entity);

    int updateLastLoginTime(Long id);

    List<String> listRoleCodesByUserId(Long userId);
}
