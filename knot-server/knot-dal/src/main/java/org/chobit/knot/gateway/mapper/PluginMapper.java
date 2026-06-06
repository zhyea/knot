package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.PluginEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PluginMapper {

    List<PluginEntity> list(@Param("keyword") String keyword,
                            @Param("status") String status);

    PluginEntity getById(Long id);

    int insert(PluginEntity entity);

    int updateStatus(PluginEntity entity);
}
