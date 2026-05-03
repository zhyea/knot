package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PingMapper {

    String selectNow();
}
