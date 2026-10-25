package org.chobit.knot.gateway.dal.mapper;

import org.chobit.knot.gateway.dal.entity.UserEntity;
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

    /**
     * 改码守卫：统计登录名被外部按业务码引用的总处数。
     *
     * <p>三类引用：应用负责人（kb_apps.owner_username）、消费者归属用户
     * （kb_routing_consumers.user_username）、路由规则归属用户（kb_routing_rules.username）。
     * kb_apps 逻辑删除后无恢复入口，故按 is_deleted = 0 过滤；另两表无逻辑删除列。</p>
     */
    Long countBindingsByUsername(@Param("username") String username);

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
