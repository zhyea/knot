package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    List<UserEntity> listUsers(@Param("keyword") String keyword);

    UserEntity getUserById(Long id);

    UserEntity getUserByUsername(String username);

    /** 按部门业务码统计用户数（ks_users.dept_code 存业务码） */
    Long countByDeptCode(@Param("deptCode") String deptCode);

    int insertUser(UserEntity entity);

    int updateUserStatus(UserEntity entity);

    int updateUser(UserEntity entity);

    int updateUserPassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    int updateLastLoginTime(Long id);

    List<String> listRoleCodesByUserId(Long userId);

    List<Map<String, Object>> listRoleNamesByUserIds(@Param("userIds") List<Long> userIds);

    List<Long> listRoleIdsByUserId(@Param("userId") Long userId);

    int deleteUserRoles(@Param("userId") Long userId);

    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);
}
