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

    Long countCredentialsByAppId(Long appId);

    Long countModelPermissionsByAppId(Long appId);
}
