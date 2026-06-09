package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.PluginBindingEntity;
import org.chobit.knot.gateway.entity.PluginInstanceEntity;

import java.util.List;

@Mapper
public interface PluginInstanceMapper {

    List<PluginInstanceEntity> list(@Param("keyword") String keyword,
                                    @Param("status") String status);

    PluginInstanceEntity getById(Long id);

    int insert(PluginInstanceEntity entity);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    List<PluginBindingEntity> listActiveBindings(@Param("extensionPoint") String extensionPoint,
                                                 @Param("stageCode") String stageCode);
}
