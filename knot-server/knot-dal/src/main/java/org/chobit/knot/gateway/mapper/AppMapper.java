package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.AppEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppMapper {

    List<AppEntity> list(@Param("keyword") String keyword);

    AppEntity getById(Long id);

    Long countByAppId(String appId);

    int insert(AppEntity entity);

    int update(AppEntity entity);

    int softDelete(Long id);

    /** 按应用业务码统计凭据数（kb_app_credentials.app_id 存业务码） */
    Long countCredentialsByAppId(@Param("appId") String appId);

    /** 按应用主键 id 统计模型权限数（kb_app_model_permissions.app_id 存主键 id） */
    Long countModelPermissionsByAppId(Long appId);

    Long countByDeptId(Long deptId);
}
