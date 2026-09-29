package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.AppEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppMapper {

    List<AppEntity> list(@Param("keyword") String keyword);

    AppEntity getById(Long id);

    Long countByAppCode(String appCode);

    int insert(AppEntity entity);

    int update(AppEntity entity);

    int softDelete(Long id);

    /** 按应用业务码统计凭据数（kb_app_credentials.app_code 存业务码） */
    Long countCredentialsByAppCode(@Param("appCode") String appCode);

    /** 按应用主键 id 统计模型权限数（kb_app_model_permissions.app_id 存主键 id，与业务码语义不同） */
    Long countModelPermissionsByAppId(Long appId);

    Long countByDeptId(Long deptId);
}
