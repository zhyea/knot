package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.PluginEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PluginMapper {

    List<PluginEntity> list();

    PluginEntity getById(Long id);

    int insert(PluginEntity entity);

    int updateStatus(PluginEntity entity);
}
