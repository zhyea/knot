package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.AppCredentialEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppCredentialMapper {

    AppCredentialEntity getByAppKey(@Param("appKey") String appKey);
}
