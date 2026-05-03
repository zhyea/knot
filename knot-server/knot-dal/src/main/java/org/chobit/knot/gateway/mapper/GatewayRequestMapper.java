package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.GatewayRequestEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GatewayRequestMapper {

    int insert(GatewayRequestEntity entity);
}
