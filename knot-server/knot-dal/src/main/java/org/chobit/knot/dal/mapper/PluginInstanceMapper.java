package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.PluginInstanceEntity;

import java.util.List;

@Mapper
public interface PluginInstanceMapper {

    List<PluginInstanceEntity> list(@Param("keyword") String keyword,
                                    @Param("status") Integer status,
                                    @Param("pluginType") String pluginType);

    PluginInstanceEntity getById(Long id);

    int insert(PluginInstanceEntity entity);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateDeleted(@Param("id") Long id, @Param("isDeleted") Integer isDeleted);

    List<PluginInstanceEntity> listActiveBindings(@Param("extensionPoint") String extensionPoint,
                                                   @Param("stageCode") String stageCode);

    List<PluginInstanceEntity> listAll();

    int setOrphaned(@Param("id") Long id, @Param("orphaned") Integer orphaned);
}
