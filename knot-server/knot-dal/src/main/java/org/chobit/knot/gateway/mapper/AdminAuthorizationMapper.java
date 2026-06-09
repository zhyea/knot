package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.AdminApiPermissionBindingEntity;
import org.chobit.knot.gateway.entity.AdminMenuEntity;

import java.util.List;

@Mapper
public interface AdminAuthorizationMapper {

    List<String> listPermissionCodesByUserId(@Param("userId") Long userId);

    AdminApiPermissionBindingEntity getApiPermissionBinding(@Param("httpMethod") String httpMethod,
                                                            @Param("pathPattern") String pathPattern);

    List<AdminMenuEntity> listMenusByUserId(@Param("userId") Long userId);
}
