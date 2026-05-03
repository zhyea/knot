package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.AppEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AppMapper {

    List<AppEntity> list();

    AppEntity getById(Long id);

    int insert(AppEntity entity);

    int update(AppEntity entity);

    Long countRequestsByAppId(Long appId);

    Long countSuccessByAppId(Long appId);

    Long sumTokensByAppId(Long appId);
}
